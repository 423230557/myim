package com.sk.weichat.bean.message;

/**
 * Created by Administrator on 2017/6/28.
 * 同步聊天记录
 * 处理服务器返回的聊天记录实体
 */

public class ChatRecord {

    String body;

    String message;

    String messageId;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
