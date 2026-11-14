/**
 * Native Bridge for CryptoJS
 * 
 * This script creates a global `CryptoJS` object that mimics the original library's API
 * but delegates the actual cryptographic operations to the high-performance native
 * implementation provided by the app's `JsEngine`.
 * 
 * This allows scripts to use standard `CryptoJS` syntax like:
 * - CryptoJS.MD5("message").toString()
 * - CryptoJS.AES.decrypt(data, key, { mode, padding }).toString(CryptoJS.enc.Utf8)
 * 
 * ...while benefiting from the speed and reliability of native Java/Kotlin crypto libraries.
 */
var CryptoJS = (function () {

    // --- Private Helper ---
    // The core bridge function to call native crypto operations
    function _nativeCrypto(algorithm, operation, args) {
        const resultJson = NativeInterface.crypto(algorithm, operation, JSON.stringify(args));
        // Only parse if it's our specific error object format.
        if (resultJson && typeof resultJson === 'string' && resultJson.startsWith('{"nativeError"')) {
            try {
                return JSON.parse(resultJson);
            } catch (e) {
                // Should not happen if the check is correct, but for safety:
                return { nativeError: "Failed to parse native error JSON." };
            }
        }
        // Otherwise, it's the successfully decrypted string.
        return resultJson;
    }

    // Creates a mock WordArray object that the jmcomic script can use.
    function createWordArrayResult(data) {
        return {
            data: data, // Store the raw result
            toString: function (encoding) {
                // The actual result is already a string from the native side.
                // We ignore the encoding hint (like Utf8) because the native
                // side has already handled correct string encoding.
                return this.data;
            }
        };
    }

    // SHA-256: use this for anything security-relevant.
    const SHA256 = function (message) {
        const hash = _nativeCrypto('sha256', 'hash', [message]);
        return createWordArrayResult(hash);
    };

    // MD5 interop shim. Two bundled tools (bilibili signature `w_rid`, jmcomic protocol
    // hashing) call third-party endpoints that require MD5, so the bridge must keep it.
    // It is NOT a security primitive: never use it for passwords, tokens or integrity.
    const MD5 = function (message) {
        const hash = _nativeCrypto('md5', 'hash', [message]);
        return createWordArrayResult(hash);
    };

    // AES Algorithm
    const AES = {
        decrypt: function (ciphertext, key, cfg) {
            // Authenticated-mode bridge: the ciphertext layout is
            //   base64( IV[12] || GCM ciphertext+tag )
            // so every message carries its own IV and the tag is verified by the
            // native side. ECB and unauthenticated CBC are intentionally not exposed.
            const keyString = (key && typeof key === 'object' && key.data) ? key.data : String(key);

            const decrypted = _nativeCrypto('aes', 'decrypt', [ciphertext, keyString]);

            // Check if native side returned an error object
            if (decrypted && decrypted.nativeError) {
                // To maintain compatibility with CryptoJS error handling, we can't throw here.
                // We return a result that will hopefully fail in a meaningful way later.
                // Returning an empty string is a common pattern.
                console.error("Native decryption failed:", decrypted.nativeError);
                return createWordArrayResult("");
            }

            return createWordArrayResult(decrypted);
        }
    };

    // Encodings (placeholders to satisfy the API)
    const enc = {
        Hex: {
            parse: function (hexStr) {
                // This is used by the JM script to parse the key.
                // We don't need to do anything but return a WordArray-like object
                // containing the raw hex string, as our native bridge will handle it.
                return createWordArrayResult(hexStr);
            }
        },
        Utf8: {
            // This is just a marker object used in `toString(CryptoJS.enc.Utf8)`.
            // No implementation needed.
        }
    };

    // Padding Schemes (placeholders to satisfy the API)
    const pad = {
        Pkcs7: {} // Marker object, no implementation needed.
    };

    // Modes
    // ECB is deliberately absent: it is not exposed to scripts any more.
    // The bridge always uses authenticated GCM with a per-message IV.
    const mode = {};

    // --- Export Public API ---
    return {
        MD5: MD5,
        SHA256: SHA256,
        AES: AES,
        enc: enc,
        pad: pad,
        mode: mode
    };
})(); 