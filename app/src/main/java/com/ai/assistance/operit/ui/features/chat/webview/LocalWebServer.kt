package com.ai.assistance.operit.ui.features.chat.webview

import android.content.Context
import android.os.Environment
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.BinaryFileContentData
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.ui.features.chat.webview.workspace.WorkspaceAccessPolicy
import com.ai.assistance.operit.ui.features.chat.webview.workspace.workspaceMimeTypeForPath
import com.ai.assistance.operit.util.AppLogger
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileDescriptor
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class FileApiEntry(val name: String, val isDirectory: Boolean)

/**
 * LocalWebServer - NanoHTTPD server that serves the files inside the workspace directory.
 *
 * It only exists for the on-device WebView workspace preview, so it binds to the loopback address:
 * binding the wildcard address let every device on the same Wi-Fi read workspace files and use this
 * device as a proxy. The `/api/` routes additionally require [apiToken], because any page that can
 * reach loopback (including a third-party site opened in the WebView) can send requests to
 * 127.0.0.1.
 */
class LocalWebServer
private constructor(
    private val context: Context,
    private val port: Int,
    initialRootPath: String,
    private val type: ServerType
) : NanoHTTPD(LOOPBACK_HOST, port) {

    @Volatile
    private var rootPath: String = initialRootPath
    @Volatile
    private var workspaceEnv: String? = null
    private val proxyClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val serverLock = Any()

    enum class ServerType {
        WORKSPACE
    }

    companion object {
        private const val TAG = "LocalWebServer"
        private const val SOCK_CLOEXEC = 0x80000

        /** Listen on the loopback address only: the workspace preview serves the on-device WebView. */
        private const val LOOPBACK_HOST = "127.0.0.1"

        /** Token required by the `/api/` routes, sent as the [API_TOKEN_HEADER] header or the [API_TOKEN_QUERY] query parameter. */
        const val API_TOKEN_HEADER = "X-Operit-Workspace-Token"
        const val API_TOKEN_QUERY = "token"

        /**
         * API token generated at random per process. It is injected into the pages this server
         * serves; a local page that does not hold it cannot call the `/api/` routes, so it can
         * neither use this device as an open proxy nor enumerate the workspace directory.
         */
        val apiToken: String = UUID.randomUUID().toString()

        // Port constants
        const val WORKSPACE_PORT = 8093

        @Volatile
        private var instances = mutableMapOf<ServerType, LocalWebServer>()

        @Synchronized
        fun getInstance(context: Context, type: ServerType): LocalWebServer {
            return instances.getOrPut(type) {
                val server: LocalWebServer = when (type) {
                    ServerType.WORKSPACE -> {
                        val workspaceRoot = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Operit/workspace"
                        )
                        LocalWebServer(
                            context.applicationContext,
                            WORKSPACE_PORT,
                            workspaceRoot.absolutePath,
                            ServerType.WORKSPACE
                        )
                    }
                }
                server
            }
        }

        private fun createSocket(address: InetAddress): FileDescriptor {
            return try {
                val domain = when (address) {
                    is Inet6Address -> OsConstants.AF_INET6
                    is Inet4Address -> OsConstants.AF_INET
                    else -> OsConstants.AF_INET
                }
                Os.socket(domain, OsConstants.SOCK_STREAM or SOCK_CLOEXEC, 0)
            } catch (e: ErrnoException) {
                throw SocketException(e.message)
            }
        }

        private fun setSocketIntOption(
            descriptor: FileDescriptor,
            level: Int,
            option: Int,
            value: Int
        ) {
            try {
                Os.setsockoptInt(descriptor, level, option, value)
            } catch (e: ErrnoException) {
                throw SocketException(e.message)
            }
        }

        private fun setSocketReceiveTimeout(descriptor: FileDescriptor, timeoutMs: Int) {
            try {
                Os.setsockoptTimeval(
                    descriptor,
                    OsConstants.SOL_SOCKET,
                    OsConstants.SO_RCVTIMEO,
                    android.system.StructTimeval.fromMillis(timeoutMs.toLong())
                )
            } catch (e: ErrnoException) {
                throw SocketException(e.message)
            }
        }
    }

    init {
        setServerSocketFactory(CloseOnExecServerSocketFactory)
    }

    @Volatile
    private var isServerRunning = false

    @Throws(IOException::class)
    override fun start() {
        synchronized(serverLock) {
            if (isServerRunning) {
                AppLogger.d(TAG, "服务器已在端口 $port 上运行，跳过启动")
                return
            }
            super.start(SOCKET_READ_TIMEOUT, false)
            isServerRunning = true
            AppLogger.d(TAG, "本地Web服务器已在端口 $port 上启动, 根目录: $rootPath")
        }
    }

    override fun stop() {
        synchronized(serverLock) {
            super.stop()
            isServerRunning = false
            AppLogger.d(TAG, "Local server stopped at port: $port")
        }
    }

    fun updateChatWorkspace(newWorkspacePath: String, newWorkspaceEnv: String?) {
        synchronized(serverLock) {
            this.rootPath = newWorkspacePath
            this.workspaceEnv = newWorkspaceEnv
            ensureWorkspaceDirExists(newWorkspacePath)
            AppLogger.d(TAG, "Workspace path updated to: $rootPath env=$workspaceEnv")
        }
    }

    fun isRunning(): Boolean = synchronized(serverLock) { isServerRunning }

    private object CloseOnExecServerSocketFactory : NanoHTTPD.ServerSocketFactory {
        override fun create(): ServerSocket {
            return CloseOnExecServerSocket()
        }
    }

    private class CloseOnExecServerSocket : ServerSocket() {
        private var listenerFd: FileDescriptor? = null
        private var bound = false
        private var closed = false
        private var localAddress: InetSocketAddress? = null
        private var reuseAddress = false
        private var receiveTimeoutMs = 0

        override fun bind(endpoint: SocketAddress?) {
            bind(endpoint, 50)
        }

        override fun bind(endpoint: SocketAddress?, backlog: Int) {
            if (closed) throw SocketException("Socket is closed")
            if (bound) throw SocketException("Already bound")
            val socketAddress = when (endpoint) {
                null -> InetSocketAddress(0)
                is InetSocketAddress -> endpoint
                else -> throw IllegalArgumentException("Unsupported address type")
            }
            if (socketAddress.isUnresolved) throw SocketException("Unresolved address")
            val descriptor = createSocket(socketAddress.address)
            try {
                setSocketIntOption(descriptor, OsConstants.SOL_SOCKET, OsConstants.SO_REUSEADDR, if (reuseAddress) 1 else 0)
                setSocketReceiveTimeout(descriptor, receiveTimeoutMs)
                Os.bind(descriptor, socketAddress.address, socketAddress.port)
                Os.listen(descriptor, if (backlog < 1) 50 else backlog)
                listenerFd = descriptor
                localAddress = Os.getsockname(descriptor) as InetSocketAddress
                bound = true
            } catch (e: ErrnoException) {
                try {
                    Os.close(descriptor)
                } catch (_: ErrnoException) {
                }
                throw SocketException(e.message)
            }
        }

        override fun accept(): Socket {
            if (closed) throw SocketException("Socket is closed")
            if (!bound) throw SocketException("Socket is not bound yet")
            val acceptedAddress = InetSocketAddress(0)
            val descriptor = listenerFd ?: throw SocketException("Socket is closed")
            val acceptedFd = try {
                Os.accept(descriptor, acceptedAddress)
            } catch (e: ErrnoException) {
                throw SocketException(e.message)
            }
            return CloseOnExecSocket(acceptedFd, acceptedAddress)
        }

        override fun close() {
            if (closed) return
            closed = true
            val descriptor = listenerFd ?: return
            listenerFd = null
            try {
                Os.close(descriptor)
            } catch (e: ErrnoException) {
                throw SocketException(e.message)
            }
        }

        override fun isBound(): Boolean = bound

        override fun isClosed(): Boolean = closed

        override fun getInetAddress(): InetAddress? = localAddress?.address

        override fun getLocalPort(): Int = localAddress?.port ?: -1

        override fun getLocalSocketAddress(): SocketAddress? = localAddress

        override fun setReuseAddress(on: Boolean) {
            reuseAddress = on
            listenerFd?.let {
                setSocketIntOption(it, OsConstants.SOL_SOCKET, OsConstants.SO_REUSEADDR, if (on) 1 else 0)
            }
        }

        override fun getReuseAddress(): Boolean = reuseAddress

        override fun setSoTimeout(timeout: Int) {
            receiveTimeoutMs = timeout
            listenerFd?.let { setSocketReceiveTimeout(it, timeout) }
        }

        override fun getSoTimeout(): Int = receiveTimeoutMs
    }

    private class CloseOnExecSocket(
        private var socketFd: FileDescriptor?,
        private val remoteAddress: InetSocketAddress
    ) : Socket() {
        private var closed = false

        override fun isClosed(): Boolean {
            return closed
        }

        override fun isConnected(): Boolean {
            return !closed
        }

        override fun getInputStream(): InputStream {
            val descriptor = requireDescriptor()
            return object : InputStream() {
                override fun read(): Int {
                    val buffer = ByteArray(1)
                    val count = read(buffer, 0, 1)
                    return if (count == -1) -1 else buffer[0].toInt() and 0xff
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (length == 0) return 0
                    return try {
                        val count = Os.read(descriptor, buffer, offset, length)
                        if (count == 0) -1 else count
                    } catch (e: java.io.InterruptedIOException) {
                        throw SocketTimeoutException(e.message)
                    } catch (e: ErrnoException) {
                        throw SocketException(e.message)
                    }
                }
            }
        }

        override fun getOutputStream(): OutputStream {
            val descriptor = requireDescriptor()
            return object : OutputStream() {
                override fun write(value: Int) {
                    val buffer = byteArrayOf(value.toByte())
                    write(buffer, 0, 1)
                }

                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    var written = 0
                    while (written < length) {
                        val count = try {
                            Os.write(descriptor, buffer, offset + written, length - written)
                        } catch (e: java.io.InterruptedIOException) {
                            throw SocketTimeoutException(e.message)
                        } catch (e: ErrnoException) {
                            throw SocketException(e.message)
                        }
                        written += count
                    }
                }
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            val descriptor = socketFd ?: return
            socketFd = null
            try {
                Os.close(descriptor)
            } catch (e: ErrnoException) {
                throw SocketException(e.message)
            }
        }

        override fun shutdownInput() {
            shutdown(OsConstants.SHUT_RD)
        }

        override fun shutdownOutput() {
            shutdown(OsConstants.SHUT_WR)
        }

        override fun setSoTimeout(timeout: Int) {
            setSocketReceiveTimeout(requireDescriptor(), timeout)
        }

        override fun getInetAddress(): InetAddress = remoteAddress.address

        override fun getPort(): Int = remoteAddress.port

        override fun getRemoteSocketAddress(): SocketAddress = remoteAddress

        override fun getLocalSocketAddress(): SocketAddress? {
            val descriptor = requireDescriptor()
            return try {
                Os.getsockname(descriptor)
            } catch (e: ErrnoException) {
                throw SocketException(e.message)
            }
        }

        private fun requireDescriptor(): FileDescriptor {
            return socketFd ?: throw SocketException("Socket is closed")
        }

        @Throws(IOException::class)
        private fun shutdown(how: Int) {
            try {
                Os.shutdown(requireDescriptor(), how)
            } catch (e: ErrnoException) {
                throw SocketException(e.message)
            }
        }
    }

    override fun serve(session: IHTTPSession): Response {
        AppLogger.d(TAG, "Request received: ${session.uri} at port $port")

        // API route for file listing
        if (session.uri.startsWith("/api/")) {
            return handleApiRequest(session)
        }

        // Serve static files
        val uri = if (session.uri == "/") "/index.html" else session.uri
        val mimeType = getCustomMimeType(uri)

        return if (type == ServerType.WORKSPACE && !workspaceEnv.isNullOrBlank()) {
            serveWorkspaceFileViaTool(uri, mimeType, session.headers["origin"])
        } else {
            serveFileFromDisk(uri, mimeType, session.headers["origin"])
        }
    }

    private fun normalizeWebPath(path: String): String {
        var p = path.trim()
        if (!p.startsWith("/")) p = "/$p"
        while (p.contains("//")) p = p.replace("//", "/")
        return p
    }

    private fun isSafeRelativeWebPath(path: String): Boolean {
        val normalized = normalizeWebPath(path)
        if (normalized.contains("\\")) return false
        return !normalized.split('/').any { it == ".." }
    }

    private fun joinVirtualRoot(root: String, uri: String): String {
        val base = normalizeWebPath(root)
        val rel = normalizeWebPath(uri)
        return if (base == "/") rel else normalizeWebPath(base.trimEnd('/') + rel)
    }

    private fun serveWorkspaceFileViaTool(uri: String, mimeType: String, origin: String?): Response {
        if (!isSafeRelativeWebPath(uri)) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Access denied").addCorsHeaders(origin)
        }

        return try {
            val toolHandler = AIToolHandler.getInstance(context)
            val fullPath = joinVirtualRoot(rootPath, uri)
            val tool = AITool(
                name = "read_file_binary",
                parameters = listOf(
                    ToolParameter("path", fullPath),
                    ToolParameter("environment", workspaceEnv ?: "")
                )
            )

            AppLogger.d(TAG, "execute read_file_binary path=$fullPath env=$workspaceEnv")
            val result = toolHandler.executeTool(tool)
            AppLogger.d(TAG, "result read_file_binary success=${result.success} error=${result.error}")

            if (!result.success || result.result !is BinaryFileContentData) {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    result.error ?: "File not found"
                ).addCorsHeaders(origin)
            }

            val data = result.result as BinaryFileContentData
            val base64Content = data.contentBase64
            val bytes = Base64.decode(base64Content, Base64.DEFAULT)

            if (mimeType == "text/html") {
                val htmlContent = String(bytes, Charsets.UTF_8)
                val injectedHtml = injectWorkspaceApiToken(injectErudaIntoHtml(htmlContent))
                return newFixedLengthResponse(Response.Status.OK, mimeType, injectedHtml).addCorsHeaders(origin)
            }

            val inputStream = ByteArrayInputStream(bytes)
            val response = newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, bytes.size.toLong())
            response.addCorsHeaders(origin)
            response
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error serving workspace file via tool", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Could not read file.").addCorsHeaders(origin)
        }
    }

    private fun serveFileFromDisk(uri: String, mimeType: String, origin: String?): Response {
        val file = File(rootPath, uri)

        if (!file.exists() || !isInRoot(file)) {
            AppLogger.w(TAG, "File not found or access denied: ${file.absolutePath}")
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "File not found"
            ).addCorsHeaders(origin)
        }

        return try {
            // Read the file into a byte array to serve it directly.
            // This avoids the GZIP streaming issue with WebView that causes "Broken pipe".
            val bytes = file.readBytes()

            if (mimeType == "text/html") {
                val htmlContent = String(bytes, Charsets.UTF_8)
                val injectedHtml = injectWorkspaceApiToken(injectErudaIntoHtml(htmlContent))
                return newFixedLengthResponse(Response.Status.OK, mimeType, injectedHtml).addCorsHeaders(origin)
            }

            // Serve the file from a byte array input stream. This is the robust way to avoid GZIP issues.
            val inputStream = ByteArrayInputStream(bytes)
            val response = newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, bytes.size.toLong())
            response.addCorsHeaders(origin)
            response
        } catch (ioe: IOException) {
            AppLogger.e(TAG, "Could not read workspace file: ${file.absolutePath}", ioe)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Could not read file."
            ).addCorsHeaders(origin)
        }
    }

    /**
     * Hand the workspace API token to pages served by this server so they can still call
     * `/api/files`. The token only appears in the HTML this server returns; it is never written into
     * the workspace files on disk.
     */
    private fun injectWorkspaceApiToken(htmlContent: String): String {
        val tokenScript =
            """
            <script>
            window.__operitWorkspaceApiToken = '$apiToken';
            window.__operitWorkspaceApiBase = 'http://127.0.0.1:$port/api/';
            </script>
            """.trimIndent()
        return injectHtmlAtHeadStart(htmlContent, tokenScript)
    }

    private fun injectErudaIntoHtml(htmlContent: String): String {
        val consoleBootstrapScript =
            """
            <script>
            (function() {
                if (window.__operitConsoleBootstrapInstalled) { return; }
                window.__operitConsoleBootstrapInstalled = true;
                var originalConsole = window.console || {};
                var levels = ['log', 'info', 'warn', 'error', 'debug'];
                var buffer = window.__operitEarlyConsoleBuffer = window.__operitEarlyConsoleBuffer || [];
                window.__operitConsoleReplayInProgress = false;
                window.__operitOriginalConsoleMethods = window.__operitOriginalConsoleMethods || {};

                levels.forEach(function(level) {
                    if (window.__operitOriginalConsoleMethods[level]) {
                        return;
                    }
                    var original = typeof originalConsole[level] === 'function'
                        ? originalConsole[level].bind(originalConsole)
                        : (typeof originalConsole.log === 'function'
                            ? originalConsole.log.bind(originalConsole)
                            : function() {});
                    window.__operitOriginalConsoleMethods[level] = original;
                    originalConsole[level] = function() {
                        if (!window.__operitConsoleReplayInProgress) {
                            buffer.push({
                                level: level,
                                args: Array.prototype.slice.call(arguments),
                                timestamp: Date.now()
                            });
                            if (buffer.length > 500) {
                                buffer.shift();
                            }
                        }
                        return original.apply(null, arguments);
                    };
                });

                window.addEventListener('error', function(event) {
                    if (window.__operitConsoleReplayInProgress) {
                        return;
                    }
                    buffer.push({
                        level: 'error',
                        args: [
                            event && event.message ? event.message : 'Script error',
                            event && event.filename ? event.filename : '',
                            event && typeof event.lineno === 'number' ? 'line ' + event.lineno : '',
                            event && typeof event.colno === 'number' ? 'col ' + event.colno : ''
                        ],
                        timestamp: Date.now()
                    });
                });

                window.addEventListener('unhandledrejection', function(event) {
                    if (window.__operitConsoleReplayInProgress) {
                        return;
                    }
                    buffer.push({
                        level: 'error',
                        args: ['Unhandled promise rejection', event ? event.reason : undefined],
                        timestamp: Date.now()
                    });
                });
            })();
            </script>
            """.trimIndent()

        val erudaLoaderScript =
            """
            <script>
            (function() {
                if (window.erudaInjected) { return; }
                window.erudaInjected = true;
                localStorage.removeItem('eruda-entry-btn');
                var script = document.createElement('script');
                script.src = 'https://cdn.jsdelivr.net/npm/eruda';
                script.onload = function() {
                    if (!window.eruda) return;
                    eruda.init();
                    var entryBtn = eruda.get('entry');
                    if (entryBtn) {
                        entryBtn.position({
                            x: 10,
                            y: window.innerHeight - 70
                        });
                    }
                    var buffer = window.__operitEarlyConsoleBuffer || [];
                    if (buffer.length > 0) {
                        var replayConsole = window.console || {};
                        window.__operitConsoleReplayInProgress = true;
                        try {
                            buffer.forEach(function(entry) {
                                var level = entry && entry.level ? entry.level : 'log';
                                var logger = typeof replayConsole[level] === 'function'
                                    ? replayConsole[level]
                                    : replayConsole.log;
                                if (typeof logger === 'function') {
                                    logger.apply(replayConsole, entry.args || []);
                                }
                            });
                        } finally {
                            window.__operitEarlyConsoleBuffer = [];
                            window.__operitConsoleReplayInProgress = false;
                        }
                    }
                };
                var parent = document.head || document.body || document.documentElement;
                if (!parent) {
                    return;
                }
                parent.appendChild(script);
            })();
            </script>
            """.trimIndent()

        val withBootstrap = injectHtmlAtHeadStart(htmlContent, consoleBootstrapScript)
        return injectHtmlBeforeBodyEnd(withBootstrap, erudaLoaderScript)
    }

    private fun injectHtmlAtHeadStart(
        htmlContent: String,
        snippet: String
    ): String {
        val headRegex = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE)
        val headMatch = headRegex.find(htmlContent)
        if (headMatch != null) {
            val insertIndex = headMatch.range.last + 1
            return htmlContent.substring(0, insertIndex) + snippet + htmlContent.substring(insertIndex)
        }

        val htmlRegex = Regex("<html\\b[^>]*>", RegexOption.IGNORE_CASE)
        val htmlMatch = htmlRegex.find(htmlContent)
        if (htmlMatch != null) {
            val insertIndex = htmlMatch.range.last + 1
            return htmlContent.substring(0, insertIndex) + "<head>$snippet</head>" + htmlContent.substring(insertIndex)
        }

        return snippet + htmlContent
    }

    private fun injectHtmlBeforeBodyEnd(
        htmlContent: String,
        snippet: String
    ): String {
        val bodyCloseRegex = Regex("</body>", RegexOption.IGNORE_CASE)
        val bodyCloseMatch = bodyCloseRegex.find(htmlContent)
        if (bodyCloseMatch != null) {
            val insertIndex = bodyCloseMatch.range.first
            return htmlContent.substring(0, insertIndex) + snippet + htmlContent.substring(insertIndex)
        }

        return htmlContent + snippet
    }

    /**
     * Directory containment check. It has to compare "root + separator": otherwise a sibling such as
     * `workspace-secrets` or `workspaceBackup`, which merely shares the root's prefix, is treated as
     * being inside the workspace and served to the caller.
     */
    private fun isInRoot(file: File): Boolean {
        return try {
            val rootDir = File(rootPath).canonicalFile
            val target = file.canonicalFile
            target == rootDir || target.path.startsWith(rootDir.path + File.separator)
        } catch (e: IOException) {
            AppLogger.e(TAG, "Error checking file path: ${e.message}", e)
            false
        }
    }

    private fun handleApiRequest(session: IHTTPSession): Response {
        val uri = session.uri
        if (!hasValidApiToken(session)) {
            AppLogger.w(TAG, "Refused $uri: missing or wrong workspace API token")
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Access denied")
                .addCorsHeaders(session.headers["origin"])
        }
        return when {
            uri.startsWith("/api/proxy") -> {
                handleProxyRequest(session)
            }
            uri.startsWith("/api/files") -> {
                val path = session.parameters["path"]?.get(0) ?: ""
                listDirectory(path, session.headers["origin"])
            }
            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "API endpoint not found").addCorsHeaders(session.headers["origin"])
            }
        }
    }

    /**
     * Token check for the `/api/` routes. The token may arrive as a query parameter (a browser
     * preflight does not carry custom headers but does keep the query string) or in the
     * [API_TOKEN_HEADER] header. The comparison is constant-time so the token cannot be inferred
     * from response timings.
     */
    private fun hasValidApiToken(session: IHTTPSession): Boolean {
        val presented = WorkspaceAccessPolicy.presentedApiToken(
            session.parameters[API_TOKEN_QUERY]?.firstOrNull(),
            session.headers,
            API_TOKEN_HEADER
        )
        return WorkspaceAccessPolicy.apiTokenMatches(presented, apiToken)
    }

    private fun handleProxyRequest(session: IHTTPSession): Response {
        val targetUrl = session.parameters["url"]?.firstOrNull()?.trim().orEmpty()
        if (targetUrl.isBlank()) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing url").addCorsHeaders(session.headers["origin"])
        }
        if (!WorkspaceAccessPolicy.isSupportedProxyTarget(targetUrl)) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Unsupported url").addCorsHeaders(session.headers["origin"])
        }

        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "").addCorsHeaders(session.headers["origin"])
        }

        val bodyBytes = readRequestBody(session)
        val contentType = session.headers["content-type"]
        val requestBody = if (session.method == Method.GET || session.method == Method.HEAD) {
            null
        } else {
            bodyBytes.toRequestBody(contentType?.toMediaTypeOrNull())
        }

        val requestBuilder = Request.Builder().url(targetUrl).method(session.method.name, requestBody)
        session.headers.forEach { (name, value) ->
            // The denylist and the reason behind each entry live in WorkspaceAccessPolicy, which is
            // covered by JVM unit tests.
            if (!WorkspaceAccessPolicy.forwardsProxyRequestHeader(name, API_TOKEN_HEADER)) {
                return@forEach
            }
            requestBuilder.addHeader(name, value)
        }

        return try {
            val response = proxyClient.newCall(requestBuilder.build()).execute()
            val status = Response.Status.lookup(response.code) ?: Response.Status.OK
            val body = response.body
            val mimeType = body?.contentType()?.toString() ?: "application/octet-stream"
            val responseStream = body?.byteStream()?.let { stream ->
                ResponseBodyInputStream(response, stream)
            }
            val nanoResponse = if (responseStream != null) {
                val contentLength = body?.contentLength() ?: -1L
                if (contentLength >= 0) {
                    newFixedLengthResponse(status, mimeType, responseStream, contentLength)
                } else {
                    newChunkedResponse(status, mimeType, responseStream)
                }
            } else {
                response.close()
                newFixedLengthResponse(status, mimeType, "")
            }

            response.headers.forEach { (name, value) ->
                if (!WorkspaceAccessPolicy.forwardsProxyResponseHeader(name)) return@forEach
                nanoResponse.addHeader(name, value)
            }
            nanoResponse.addCorsHeaders(session.headers["origin"])
        } catch (e: Exception) {
            AppLogger.e(TAG, "Proxy request failed: $targetUrl", e)
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Proxy error: ${e.message}").addCorsHeaders(session.headers["origin"])
        }
    }

    private fun readRequestBody(session: IHTTPSession): ByteArray {
        return try {
            val tempFiles = HashMap<String, String>()
            session.parseBody(tempFiles)
            val postDataPath = tempFiles["postData"]
            if (postDataPath != null) {
                File(postDataPath).readBytes()
            } else {
                ByteArray(0)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read proxy request body", e)
            ByteArray(0)
        }
    }

    private fun listDirectory(relativePath: String, origin: String?): Response {
        try {
            val toolHandler = AIToolHandler.getInstance(context)

            val requestedPath = if (type == ServerType.WORKSPACE && !workspaceEnv.isNullOrBlank()) {
                if (!isSafeRelativeWebPath(relativePath)) {
                    return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Access denied").addCorsHeaders(origin)
                }
                joinVirtualRoot(rootPath, relativePath)
            } else {
                // Security check: ensure the path is within our root directory
                val requestedDir = File(rootPath, relativePath).canonicalFile
                if (!isInRoot(requestedDir)) {
                    return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Access denied").addCorsHeaders(origin)
                }
                requestedDir.absolutePath
            }

            val params = mutableListOf(ToolParameter("path", requestedPath))
            if (type == ServerType.WORKSPACE && !workspaceEnv.isNullOrBlank()) {
                params.add(ToolParameter("environment", workspaceEnv ?: ""))
            }

            val tool = AITool(
                name = "list_files",
                parameters = params
            )

            val result = toolHandler.executeTool(tool)

            if (result.success && result.result is DirectoryListingData) {
                // The result from list_files is already a JSON string of a list of file info.
                val directoryListing = result.result as DirectoryListingData
                val apiEntries = directoryListing.entries.map { FileApiEntry(it.name, it.isDirectory) }
                val jsonResult = Json.encodeToString(apiEntries)
                return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResult).addCorsHeaders(origin)
            } else {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, result.error ?: "Failed to list files").addCorsHeaders(origin)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error listing directory", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}").addCorsHeaders(origin)
        }
    }

    /**
     * Allow cross-origin reads only for the local workspace pages.
     *
     * Reflecting an arbitrary Origin together with `Access-Control-Allow-Credentials: true` lets any
     * web page read this server's responses, so only `http://localhost:<workspace port>` and
     * `http://127.0.0.1:<workspace port>` are accepted here. Every other origin gets no CORS headers
     * at all, and the browser then refuses to let it read the response. Same-origin requests never
     * needed CORS headers, so static files and the workspace pages themselves are unaffected. The
     * proxy no longer forwards credentials, so `Access-Control-Allow-Credentials` is not sent either.
     */
    private fun Response.addCorsHeaders(origin: String?): Response {
        val allowOrigin = origin?.let {
            WorkspaceAccessPolicy.allowedWorkspaceOrigin(it, WORKSPACE_PORT)
        } ?: return this
        this.addHeader("Access-Control-Allow-Origin", allowOrigin)
        this.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
        this.addHeader("Access-Control-Allow-Headers", "X-Requested-With, Content-Type, Origin, Accept, $API_TOKEN_HEADER")
        this.addHeader("Access-Control-Max-Age", "3600")
        this.addHeader("Vary", "Origin")
        return this
    }

    private class ResponseBodyInputStream(
        private val response: okhttp3.Response,
        inputStream: InputStream
    ) : FilterInputStream(inputStream) {
        override fun close() {
            try {
                super.close()
            } finally {
                response.close()
            }
        }
    }
    
    /**
     * 确保工作区目录存在
     */
    private fun ensureWorkspaceDirExists(path: String) {
        if (!workspaceEnv.isNullOrBlank()) return
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdirs()
            AppLogger.d(TAG, "创建工作区目录: $path")
        }
    }
    
    /**
     * 根据文件路径获取MIME类型
     */
    private fun getCustomMimeType(uri: String): String {
        return workspaceMimeTypeForPath(uri)
    }
}
