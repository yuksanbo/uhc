package ru.yuksanbo.common.uhc.ning

import com.ning.http.client.AsyncHandler
import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.AsyncHttpClientConfig
import com.ning.http.client.HttpResponseBodyPart
import com.ning.http.client.HttpResponseHeaders
import com.ning.http.client.HttpResponseStatus
import com.ning.http.client.Request
import com.ning.http.client.RequestBuilder
import com.ning.http.client.generators.InputStreamBodyGenerator
import com.ning.http.client.providers.netty.NettyAsyncHttpProvider
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import ru.yuksanbo.common.logging.debug
import ru.yuksanbo.common.misc.letWith
import ru.yuksanbo.common.misc.toVerboseHex
import ru.yuksanbo.common.uhc.Uhc
import ru.yuksanbo.common.uhc.UhcOperation
import ru.yuksanbo.common.uhc.UhcResponse
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit

class UhcNingImpl : Uhc {

    companion object {
        private val LOG = LoggerFactory.getLogger(UhcNingImpl::class.java)!!
    }

    val client: AsyncHttpClient

    constructor(userConfig: Config) {
        val config = userConfig.withFallback(ConfigFactory.load("yvu/libs/uhc/uhc.conf"))

        client = AsyncHttpClient(NettyAsyncHttpProvider(
                AsyncHttpClientConfig.Builder()
                        .setRequestTimeout(config.getDuration("request-timeout", TimeUnit.MILLISECONDS).toInt())
                        .setConnectTimeout(config.getDuration("connect-timeout", TimeUnit.MILLISECONDS).toInt())
                        .build()
        ))
    }

    override fun execute(uhcOp: UhcOperation) {
        val respBuilder = UhcResponse.Builder()

        val streaming = uhcOp.on200Stream != null
        var streamCallback: ((ByteArray) -> Unit)? = null
        var fullBody: ByteBuffer? = null

        client
                .prepareRequest(fun(): Request {
                    val builder = RequestBuilder()
                            .setUrl(uhcOp.path)
                            .setMethod(uhcOp.method.name.toUpperCase())

                    if (uhcOp.contentType != null) {
                        builder.setBodyEncoding(uhcOp.contentType)
                    }

                    uhcOp.headers.forEach {
                        builder.setHeader(it.key, it.value)
                    }

                    builder.setQueryParams(uhcOp.queryParams)

                    if (uhcOp.content != null) {
                        builder.setBody(uhcOp.content)
                    } else if (uhcOp.contentStream != null) {
                        builder.setBody(InputStreamBodyGenerator(uhcOp.contentStream))
                    }

                    return builder.build()
                }.invoke())
                .execute(object : AsyncHandler<Void> {
                    override fun onStatusReceived(responseStatus: HttpResponseStatus?): AsyncHandler.STATE {
                        respBuilder.statusCode(responseStatus!!.statusCode)
                        return AsyncHandler.STATE.CONTINUE
                    }

                    override fun onBodyPartReceived(bodyPart: HttpResponseBodyPart?): AsyncHandler.STATE {
                        val bytes = bodyPart!!.bodyPartBytes
                        LOG.debug { "uhc response bodypart (sz=${bytes.size}) = ${bytes.toVerboseHex()}" }

                        if (streaming) {
                            streamCallback!!.invoke(bytes)
                        } else {
                            fullBody!!.put(bytes)
                        }

                        return AsyncHandler.STATE.CONTINUE
                    }

                    override fun onHeadersReceived(headers: HttpResponseHeaders?): AsyncHandler.STATE {
                        respBuilder.headers(headers!!.headers)
                        streamCallback = uhcOp.on200Stream?.invoke(respBuilder.build())
                        return AsyncHandler.STATE.CONTINUE
                    }

                    override fun onCompleted(): Void? {
                        val resp = respBuilder.build()
                        when (resp.statusCode) {
                            200 -> {
                                if (streaming) {
                                    streamCallback!!.invoke(Uhc.EndOfStream)
                                } else {
                                    uhcOp.on200Raw!!.invoke(resp, fullBody!!.array())
                                }
                            }
                            else -> uhcOp.onNon200.invoke(resp, fullBody!!.array())
                        }
                        uhcOp.finally?.invoke()
                        return null
                    }

                    override fun onThrowable(t: Throwable?) {
                        uhcOp.onError.invoke(t!!)
                        uhcOp.finally?.invoke()
                    }
                })
    }

}
