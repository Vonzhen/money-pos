package com.money.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 🌟 架构规范：主副屏实时通讯基站 (全双工通道)
 */
@Slf4j
@Component
@ServerEndpoint("/ws/pos-sync")
public class PosSyncServer {

    // 工业级线程安全集合，用于存放所有当前在线的客显屏(可能有多台设备连接)
    private static final CopyOnWriteArraySet<Session> sessions = new CopyOnWriteArraySet<>();

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        log.info("[客显同步基站] 🟢 新客显屏接入，当前连接数: {}", sessions.size());
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        log.info("[客显同步基站] 🔴 客显屏断开，当前连接数: {}", sessions.size());
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // 🌟 核心抢救：基站收到收银台发来的指令后，必须全网广播给所有客显屏！
        broadcast(message);
        log.debug("[客显同步基站] 收到并成功转发实时指令: {}", message);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("[客显同步基站] ❌ 通讯异常", error);
    }

    /**
     * 🌟 核心规范API：由收银端 Controller 调用此方法，向所有客显屏广播最新状态
     * @param message 标准化的 JSON 字符串协议
     */
    public static void broadcast(String message) {
        for (Session session : sessions) {
            try {
                if (session.isOpen()) {
                    // 异步或同步发送文本给前端
                    session.getBasicRemote().sendText(message);
                }
            } catch (IOException e) {
                log.error("[客显同步基站] 消息广播失败", e);
            }
        }
    }
}