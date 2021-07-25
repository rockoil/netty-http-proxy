package com.hakine.apps.netty.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class NioServer {

    private int LOCAL_PORT = 0;
    private String REMOTE_HOST = "";
    private int REMOTE_PORT = 0;

    public NioServer(int LOCAL_PORT, String REMOTE_HOST, int REMOTE_PORT) {
        this.LOCAL_PORT = LOCAL_PORT;
        this.REMOTE_HOST = REMOTE_HOST;
        this.REMOTE_PORT = REMOTE_PORT;
    }

    public void run() throws Exception {

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {

            ServerBootstrap sb = new ServerBootstrap();
            sb.group(bossGroup, workerGroup)
                    .channel(NioSctpServerChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))

                    .childOption(ChannelOption.AUTO_READ, false)
                    .bind(LOCAL_PORT).sync().channel().closeFuture().sync();

        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}
