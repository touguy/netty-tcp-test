// src/main/java/com/example/tcp/TcpClientRunner.java
package com.example.tcp;

import com.example.tcp.proto.PacketProto;
import com.example.tcp.proto.PacketProto.Packet;
import com.example.tcp.proto.PacketProto.Packet.PacketType;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
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
                     
                     // 1. 수신 (Inbound): 프레임 디코더 -> Protobuf 디코더
                     p.addLast(new ProtobufVarint32FrameDecoder());
                     p.addLast(new ProtobufDecoder(PacketProto.Packet.getDefaultInstance()));
                     
                     // 2. 송신 (Outbound): 길이 프리펜더 -> Protobuf 인코더
                     p.addLast(new ProtobufVarint32LengthFieldPrepender());
                     p.addLast(new ProtobufEncoder());
                     
                     // 3. 클라이언트 비즈니스 로직 핸들러
                     p.addLast(new SimpleChannelInboundHandler<Packet>() {
                         @Override
                         public void channelActive(ChannelHandlerContext ctx) {
                             log.info("Connected to Server!");
                             // 사실: 외부에서 받은 payload로 Packet을 생성하여 전송합니다.
                             Packet msg = Packet.newBuilder()
                                     .setType(PacketType.DATA)
                                     .setPayload(payload) 
                                     .build();
                             ctx.writeAndFlush(msg);
                         }

                         @Override
                         protected void channelRead0(ChannelHandlerContext ctx, Packet msg) {
                            log.info("Received from Server: [Type={}] [Payload={}] [Status={}]", 
                                      msg.getType(), msg.getPayload(), msg.getStatusCode());

                            // ⭐ Heartbeat 응답 로직 추가 ⭐
                            if (msg.getType() == PacketType.PING) {
                                // PING을 받으면 PONG 패킷을 즉시 만들어 서버로 응답합니다.
                                Packet pongMsg = Packet.newBuilder()
                                        .setType(PacketType.PONG) // PONG 타입 사용
                                        .setPayload("PONG response")
                                        .setTimestamp(System.currentTimeMillis())
                                        .build();
                                ctx.writeAndFlush(pongMsg);
                                log.info("Sent PONG response to Server.");
                                
                            } else if (msg.getType() == PacketType.DATA) {
                                // DATA 응답 처리 (예: ECHO)
                                log.info("DATA packet processed. Keeping connection alive.");
                                
                            } else if (msg.getType() == PacketType.ERROR) {
                                log.error("Received ERROR packet! Status Code: {}", msg.getStatusCode());
                                // 에러를 받으면 연결을 닫고 애플리케이션을 종료합니다.
                                ctx.close();
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