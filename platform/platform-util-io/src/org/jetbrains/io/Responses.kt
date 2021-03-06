// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("Responses")
package org.jetbrains.io

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.util.*

private var SERVER_HEADER_VALUE: String? = null

fun response(contentType: String?, content: ByteBuf?): FullHttpResponse {
  val response = if (content == null)
    DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
  else
    DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)
  if (contentType != null) {
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
  }
  return response
}

fun response(content: CharSequence, charset: Charset = CharsetUtil.US_ASCII): FullHttpResponse {
  return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.copiedBuffer(content, charset))
}

fun HttpResponse.setDate() {
  if (!headers().contains(HttpHeaderNames.DATE)) {
    headers().set(HttpHeaderNames.DATE, Calendar.getInstance().time)
  }
}

fun HttpResponse.addNoCache(): HttpResponse {
  @Suppress("SpellCheckingInspection")
  headers().add(HttpHeaderNames.CACHE_CONTROL, "no-cache, no-store, must-revalidate, max-age=0")
  headers().add(HttpHeaderNames.PRAGMA, "no-cache")
  return this
}

val serverHeaderValue: String?
  get() {
    if (SERVER_HEADER_VALUE == null) {
      val app = ApplicationManager.getApplication()
      if (app != null && !app.isDisposed) {
        SERVER_HEADER_VALUE = ApplicationInfo.getInstance().fullApplicationName
      }
    }
    return SERVER_HEADER_VALUE
  }

fun HttpResponse.addServer() {
  serverHeaderValue?.let {
    headers().add(HttpHeaderNames.SERVER, it)
  }
}

@JvmOverloads
fun HttpResponse.send(channel: Channel, request: HttpRequest?, extraHeaders: HttpHeaders? = null) {
  if (status() !== HttpResponseStatus.NOT_MODIFIED && !HttpUtil.isContentLengthSet(this)) {
    HttpUtil.setContentLength(this,
      (if (this is FullHttpResponse) content().readableBytes() else 0).toLong())
  }

  addCommonHeaders()
  extraHeaders?.let {
    headers().add(it)
  }
  send(channel, request != null && !addKeepAliveIfNeeded(request))
}

fun HttpResponse.addKeepAliveIfNeeded(request: HttpRequest): Boolean {
  if (HttpUtil.isKeepAlive(request)) {
    HttpUtil.setKeepAlive(this, true)
    return true
  }
  return false
}

@Deprecated("The method name is grammatically incorrect", replaceWith = ReplaceWith("this.addKeepAliveIfNeeded(request)"))
fun HttpResponse.addKeepAliveIfNeed(request: HttpRequest): Boolean = addKeepAliveIfNeeded(request)

fun HttpResponse.addCommonHeaders() {
  addServer()
  setDate()
  if (!headers().contains("X-Frame-Options")) {
    headers().set("X-Frame-Options", "SameOrigin")
  }
  @Suppress("SpellCheckingInspection")
  headers().set("X-Content-Type-Options", "nosniff")
  headers().set("x-xss-protection", "1; mode=block")

  headers().set(HttpHeaderNames.ACCEPT_RANGES, "bytes")
}

fun HttpResponse.send(channel: Channel, close: Boolean) {
  if (!channel.isActive) {
    return
  }

  val future = channel.write(this)
  if (this !is FullHttpResponse) {
    channel.write(LastHttpContent.EMPTY_LAST_CONTENT)
  }
  channel.flush()
  if (close) {
    future.addListener(ChannelFutureListener.CLOSE)
  }
}

fun HttpResponseStatus.response(request: HttpRequest? = null, description: String? = null): HttpResponse = createStatusResponse(this, request, description)

@JvmOverloads
fun HttpResponseStatus.send(channel: Channel, request: HttpRequest? = null, description: String? = null, extraHeaders: HttpHeaders? = null) {
  createStatusResponse(this, request, description).send(channel, request, extraHeaders)
}

fun HttpResponseStatus.orInSafeMode(safeStatus: HttpResponseStatus): HttpResponseStatus {
  @Suppress("NullableBooleanElvis")
  return when {
    Registry.`is`("ide.http.server.response.actual.status", true) || ApplicationManager.getApplication()?.isUnitTestMode ?: false -> this
    else -> safeStatus
  }
}

private fun createStatusResponse(responseStatus: HttpResponseStatus, request: HttpRequest?, description: String?): HttpResponse {
  if (request != null && request.method() === HttpMethod.HEAD) {
    return DefaultFullHttpResponse(HttpVersion.HTTP_1_1, responseStatus, Unpooled.EMPTY_BUFFER)
  }

  val builder = StringBuilder()
  val message = responseStatus.toString()
  builder.append("<!doctype html><title>").append(message).append("</title>").append("<h1 style=\"text-align: center\">").append(message).append("</h1>")
  if (description != null) {
    builder.append("<p>").append(description).append("</p>")
  }
  builder.append("<hr/><p style=\"text-align: center\">").append(serverHeaderValue ?: "").append("</p>")

  val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, responseStatus, ByteBufUtil.encodeString(ByteBufAllocator.DEFAULT, CharBuffer.wrap(builder), CharsetUtil.UTF_8))
  response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html")
  return response
}