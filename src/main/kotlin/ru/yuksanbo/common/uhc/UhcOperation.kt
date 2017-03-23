package ru.yuksanbo.common.uhc

import org.slf4j.LoggerFactory
import java.io.InputStream
import java.util.function.Consumer

class UhcOperation {

    companion object {
        private val LOG = LoggerFactory.getLogger(UhcOperation::class.java)!!
    }

    var path: String = "/"
        private set

    var method: UhcMethod = UhcMethod.Get
        private set

    var queryParams: Map<String, List<String>> = emptyMap()
        private set

    var headers: Map<String, String> = emptyMap()
        private set

    var content: String? = null
        private set

    var contentStream: InputStream? = null
        private set

    var contentType: String? = null
        private set

    var on200Raw: ((UhcResponse, ByteArray) -> Unit)? = null
        private set

    var on200Stream: ((UhcResponse) -> (ByteArray) -> Unit)? = null
        private set

    var onNon200: (UhcResponse, ByteArray) -> Unit = { resp, bytes -> LOG.warn("Unhandled HTTP ${resp.statusCode} response") }
        private set

    var onError: (Throwable) -> Unit = { t -> LOG.error("Unhandled error while reading HTTP response", t) }
        private set

    var finally: (() -> Unit)? = null

    class Builder {

        val target = UhcOperation()

        fun get(): Builder {
            target.method = UhcMethod.Get
            return this
        }

        fun post(): Builder {
            target.method = UhcMethod.Post
            return this
        }

        fun put(): Builder {
            target.method = UhcMethod.Put
            return this
        }

        fun delete(): Builder {
            target.method = UhcMethod.Delete
            return this
        }

        fun path(path: String): Builder {
            target.path = path
            return this
        }

        fun query(key: String, value: String): Builder {
            assert(!target.queryParams.containsKey(key), { "Query parameter ${key} already exists" })
            target.queryParams += Pair(key, listOf(value))
            return this
        }

        fun query(key: String, vararg values: String): Builder {
            target.queryParams += Pair(key, values.toList())
            return this
        }

        fun header(key: String, value: String): Builder {
            assert(!target.headers.contains(key), { "Header ${key} already exists" })
            target.headers += Pair(key, value)
            return this
        }

        fun body(body: String, contentType: String = "application/json"): Builder {
            target.content = body
            target.contentType = contentType
            return this
        }

        fun bodyStream(contentInput: InputStream, contentType: String = "application/json"): Builder {
            target.contentStream = contentInput
            target.contentType = contentType
            return this
        }

        fun on200Raw(op: (UhcResponse, ByteArray) -> Unit): Builder {
            assert(target.on200Stream == null, { "Can't have other on200xxx variants set" })
            target.on200Raw = op
            return this
        }

        fun on200Stream(op: (UhcResponse) -> (ByteArray) -> Unit): Builder {
            assert(target.on200Raw == null, { "Can't have other on200xxx variants set" })
            target.on200Stream = op
            return this
        }

        fun onNon200(op: (UhcResponse, ByteArray) -> Unit): Builder {
            target.onNon200 = op
            return this
        }

        fun finally(op: () -> Unit): Builder {
            target.finally = op
            return this
        }

        fun build(): UhcOperation = target
    }
}
