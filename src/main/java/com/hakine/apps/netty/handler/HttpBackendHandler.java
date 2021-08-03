package com.hakine.apps.netty.handler;

import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;

public class HttpBackendHandler extends ChannelInboundHandlerAdapter {

    private final Channel inboundChannel;

    public HttpBackendHandler(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

        if(msg instanceof FullHttpResponse) {

        }


        inboundChannel.writeAndFlush(msg).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {
                if(channelFuture.isSuccess()) {
                    ctx.channel().read();
                } else {
                    channelFuture.channel().close();
                }
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        HttpFrontendHandler.closeOnFlush(inboundChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        HttpFrontendHandler.closeOnFlush(ctx.channel());
    }
}
