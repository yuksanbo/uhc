package ru.yuksanbo.common.uhc

class UhcResponse {

    var statusCode: Int = 0
        private set

    var headers: Map<String, List<String>> = emptyMap()
        private set

    class Builder {
        val target = UhcResponse()

        fun statusCode(code: Int): Builder {
            target.statusCode = code
            return this
        }

        fun headers(headers: Map<String, List<String>>): Builder {
            target.headers = headers
            return this
        }

        fun build(): UhcResponse = target
    }
}