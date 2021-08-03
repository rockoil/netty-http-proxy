package com.hakine.apps.netty.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.nio.charset.StandardCharsets;


public class HttpFrontendHandler extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(HttpFrontendHandler.class);

    private final String remoteHost;
    private final int remotePort;

    // As we use inboundChannel.eventLoop() when building the Bootstrap this does not need to be volatile as
    // the outboundChannel will use the same EventLoop (and therefore Thread) as the inboundChannel.
    private Channel outboundChannel;

    private boolean isSsl;

    public HttpFrontendHandler(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.isSsl = remoteHost.contains("https");
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        final Channel inboundChannel = ctx.channel();

        // Start the connection attempt.
        Bootstrap b = new Bootstrap();
        b.group(inboundChannel.eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();

                        // 원격접속 주소가 ssl 일경우 context를 추가함.
                        if(isSsl) {
                            SslContext sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                            pipeline.addLast(sslCtx.newHandler(socketChannel.alloc()));
                        }

                        pipeline
                                .addLast(new HttpClientCodec())
                                .addLast(new HttpObjectAggregator(102948573, true))
                                .addLast(new HttpBackendHandler(inboundChannel));
                    }
                })
                //.handler(new HttpBackendHandler(inboundChannel))
                .option(ChannelOption.AUTO_READ, false);

        ChannelFuture f = b.connect(remoteHost, remotePort);
        outboundChannel = f.channel();

        f.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                if(channelFuture.isSuccess()) {
                    inboundChannel.read();
                } else {
                    inboundChannel.close();
                }
            }
        });
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if(outboundChannel.isActive()) {

            // Request일 경우
            if(msg instanceof FullHttpRequest) {
                FullHttpRequest request = (FullHttpRequest) msg;

                // 원격 접속의 request 재설정
                request.setUri("https://localhost:8030/api/test");
                request.setMethod(HttpMethod.POST);
                request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.APPLICATION_JSON);
                request.headers().set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.APPLICATION_JSON);

                String jsonStr = "{\"test\": \"1234\"}";

                request.content().clear();
                request.content().writeBytes(jsonStr.getBytes(StandardCharsets.UTF_8));

                // 헤더에 요청 Body 값을 재설정 한다.
                request.headers().set(HttpHeaderNames.CONTENT_LENGTH, request.content().readableBytes());


                outboundChannel.writeAndFlush(request).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture channelFuture) throws Exception {
                        if(channelFuture.isSuccess()) {
                            // was able to flush out data, start to read the next chunk
                            ctx.channel().read();
                        } else {
                            channelFuture.channel().close();
                        }
                    }
                });
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if(outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }

    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}
