// src/main/java/com/example/tcp/TcpClientRunner.java
package com.example.tcp;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

@Slf4j
@Component
@ConfigurationProperties(prefix = "netty.server")
public class TcpClientRunner implements ApplicationRunner {

    // 사실: 서버 접속 정보는 상수로 정의되어 있습니다.
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 8888;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 1. 커맨드 라인 인자 처리 (메시지 페이로드)
        String payload;
        if (args.getNonOptionArgs().isEmpty()) {
            payload = "Hello! This is Client."; // 인자가 없으면 기본값 사용
            log.warn("Payload argument not provided. Using default payload: {}", payload);
        } else {
            // 사실: 첫 번째 커맨드 라인 인자를 메시지 페이로드로 사용합니다.
            payload = args.getNonOptionArgs().get(0);
            log.info("Using provided payload: {}", payload);
        }

        EventLoopGroup group = new NioEventLoopGroup();

        try {
            // Netty 클라이언트 부트스트랩 설정
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();

                            // 1. 수신 (Inbound): StringDecoder 디코더
                            p.addLast(new StringDecoder(CharsetUtil.UTF_8));

                            // 2. 송신 (Outbound): StringEncoder 인코더
                            p.addLast(new StringEncoder(CharsetUtil.UTF_8));

                            // 3. 클라이언트 비즈니스 로직 핸들러
                            p.addLast(new SimpleChannelInboundHandler<String>() {
                                @Override
                                public void channelActive(ChannelHandlerContext ctx) {
                                    log.info("Connected to Server!");
                                    ctx.writeAndFlush(payload);
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, String msg) {

                                    log.info("Received from Server: [Message={}]", msg);
                                    // ⭐ Heartbeat 응답 로직 추가 ⭐
                                    if ("PING".equalsIgnoreCase(msg.trim())) {
                                        // 클라이언트가 보낸 PING에 대한 응답
                                        ctx.writeAndFlush("PONG\n");
                                    } else {
                                        // 일반 메시지 처리 (Echo)
                                        // ctx.writeAndFlush("ECHO: " + msg + "\n");
                                    }
                                }

                                @Override
                                public void channelInactive(ChannelHandlerContext ctx) {
                                    log.info("Disconnected from Server. Shutting down application.");
                                    // 사실: 연결이 종료되면 Spring Boot 애플리케이션을 정상 종료(exit code 0)합니다.
                                    System.exit(0);
                                }
                            });
                        }
                    });

            // 접속 시도
            ChannelFuture f = b.connect(HOST, PORT).sync();

            // Channel이 닫힐 때까지 블로킹하여 클라이언트가 즉시 종료되는 것을 방지합니다.
            f.channel().closeFuture().sync();

        } catch (Exception e) {
            log.error("Connection failed. (Did you start the server?)", e);
            // 사실: 연결 실패 시 애플리케이션을 비정상 종료(exit code 1)합니다.
            System.exit(1);
        } finally {
            group.shutdownGracefully();
        }
    }
}