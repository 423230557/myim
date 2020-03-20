package com.sk.weichat.view.chatHolder;

import android.text.TextUtils;

import com.sk.weichat.bean.message.ChatMessage;
import com.sk.weichat.bean.message.XmppMessage;

/**
 * holder工厂
 * <p>
 * create by xuan 2018-12-6 15:12:14
 */

public class ChatHolderFactory {
    /**
     * holder 根据消息类型 产生不同的holder
     *
     * @param holderType 消息类型
     * @return
     */
    public static AChatHolderInterface getHolder(ChatHolderType holderType) {
        AChatHolderInterface holder = null;
        switch (holderType) {
            case VIEW_FROM_TEXT: // 文字消息
            case VIEW_TO_TEXT: // 文字消息
                holder = new TextViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_TEXT;
                break;
            case VIEW_FROM_REPLAY: // 回复消息，
            case VIEW_TO_REPLAY: // 回复消息，
                holder = new TextReplayViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_REPLAY;
                break;
            case VIEW_FROM_IMAGE: // 图片消息
            case VIEW_TO_IMAGE: // 图片消息
                holder = new ImageViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_IMAGE;
                break;
            case VIEW_FROM_VOICE: // 语音
            case VIEW_TO_VOICE: // 语音
                holder = new VoiceViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_VOICE;
                break;
            case VIEW_FROM_VIDEO: // 视频
            case VIEW_TO_VIDEO: // 视频
                holder = new VideoViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_VIDEO;
                break;
            case VIEW_FROM_LOCATION: // 链接
            case VIEW_TO_LOCATION: // 链接
            case VIEW_FROM_LINK: // 链接
            case VIEW_TO_LINK: // 链接
                holder = new LocationViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_LOCATION || holderType == ChatHolderType.VIEW_FROM_LINK;
                break;
            case VIEW_FROM_GIF: // 动图
            case VIEW_TO_GIF: // 动图
                holder = new GifViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_GIF;
                break;
            case VIEW_FROM_FILE: // 文件
            case VIEW_TO_FILE: // 文件
                holder = new FileViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_FILE;
                break;
            case VIEW_FROM_CARD: // 名片
            case VIEW_TO_CARD: // 名片
                holder = new CardViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_CARD;
                break;
            case VIEW_FROM_RED: // 红包
            case VIEW_TO_RED: // 红包
            case VIEW_FROM_RED_KEY: // 口令红包
            case VIEW_TO_RED_KEY: // 口令红包
                holder = new RedViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_RED || holderType == ChatHolderType.VIEW_FROM_RED_KEY;
                break;
            case VIEW_FROM_TRANSFER:
            case VIEW_TO_TRANSFER:
                holder = new TransferViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_TRANSFER;
                break;
            case VIEW_FROM_LINK_SHARE: // 分享进来的链接
            case VIEW_TO_LINK_SHARE: // 分享进来的链接
                holder = new LinkViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_LINK_SHARE;
                break;
            case VIEW_FROM_IMAGE_TEXT: // 图文消息
            case VIEW_TO_IMAGE_TEXT: // 图文消息
                holder = new TextImgViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_IMAGE_TEXT;
                break;
            case VIEW_FROM_IMAGE_TEXT_MANY: // 多条图文
            case VIEW_TO_IMAGE_TEXT_MANY: // 多条图文
                holder = new TextImgManyHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_IMAGE_TEXT_MANY;
                break;
            case VIEW_FROM_MEDIA_CALL: // 音视频
            case VIEW_TO_MEDIA_CALL: // 音视频
                holder = new CallViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_MEDIA_CALL;
                break;
            case VIEW_FROM_SHAKE: // 戳一戳
            case VIEW_TO_SHAKE: // 戳一戳
                holder = new ShakeViewHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_SHAKE;
                break;
            case VIEW_FROM_CHAT_HISTORY: // 聊天记录
            case VIEW_TO_CHAT_HISTORY: // 聊天记录
                holder = new ChatHistoryHolder();
                holder.isMysend = holderType == ChatHolderType.VIEW_FROM_CHAT_HISTORY;
                break;
            case VIEW_SYSTEM_TIP:
                holder = new SystemViewHolder();
                break;
            case VIEW_SYSTEM_LIVE:
                holder = new LiveChatViewHolder();
                break;
            default:
                holder = new SystemViewHolder();
                break;
        }

        return holder;
    }

    /**
     * 将xmppType 转换成 ViewType
     *
     * @param mySend
     * @param message
     * @return viewType
     */
    public static int xt2vt(boolean mySend, ChatMessage message) {
        ChatHolderType eType = getChatHolderType(mySend, message);
        return eType.ordinal();
    }

    /**
     * 将int 类型的 holderType 转换成 ChatHolderType
     *
     * @param holderType
     * @return ChatHolderType
     */
    public static ChatHolderType getChatHolderType(int holderType) {
        return ChatHolderType.values()[holderType];
    }

    /**
     * 返回枚举类型的个数
     *
     * @return
     */
    public static int viewholderCount() {
        return ChatHolderType.values().length;
    }

    public static ChatHolderType getChatHolderType(boolean mySend, ChatMessage message) {

        int xmppType = message.getType();
        ChatHolderType eType;
        switch (xmppType) {
            case XmppMessage.TYPE_TEXT:
                eType = mySend ? ChatHolderType.VIEW_FROM_TEXT : ChatHolderType.VIEW_TO_TEXT;
                break;
            case XmppMessage.TYPE_REPLAY:
                eType = mySend ? ChatHolderType.VIEW_FROM_REPLAY : ChatHolderType.VIEW_TO_REPLAY;
                break;
            case XmppMessage.TYPE_IMAGE:
                eType = mySend ? ChatHolderType.VIEW_FROM_IMAGE : ChatHolderType.VIEW_TO_IMAGE;
                break;
            case XmppMessage.TYPE_VOICE:
                eType = mySend ? ChatHolderType.VIEW_FROM_VOICE : ChatHolderType.VIEW_TO_VOICE;
                break;
            case XmppMessage.TYPE_VIDEO:
                eType = mySend ? ChatHolderType.VIEW_FROM_VIDEO : ChatHolderType.VIEW_TO_VIDEO;
                break;
            case XmppMessage.TYPE_GIF:
                eType = mySend ? ChatHolderType.VIEW_FROM_GIF : ChatHolderType.VIEW_TO_GIF;
                break;
            case XmppMessage.TYPE_LOCATION:
                eType = mySend ? ChatHolderType.VIEW_FROM_LOCATION : ChatHolderType.VIEW_TO_LOCATION;
                break;
            case XmppMessage.TYPE_FILE:
                eType = mySend ? ChatHolderType.VIEW_FROM_FILE : ChatHolderType.VIEW_TO_FILE;
                break;
            case XmppMessage.TYPE_RED:
                if (!TextUtils.isEmpty(message.getFilePath())) {
                    eType = mySend ? ChatHolderType.VIEW_FROM_RED_KEY : ChatHolderType.VIEW_TO_RED_KEY;
                } else {
                    eType = mySend ? ChatHolderType.VIEW_FROM_RED : ChatHolderType.VIEW_TO_RED;
                }
                break;
            case XmppMessage.TYPE_TRANSFER:
                eType = mySend ? ChatHolderType.VIEW_FROM_TRANSFER : ChatHolderType.VIEW_TO_TRANSFER;
                break;
            case XmppMessage.TYPE_CARD:
                eType = mySend ? ChatHolderType.VIEW_FROM_CARD : ChatHolderType.VIEW_TO_CARD;
                break;
            case XmppMessage.TYPE_LINK: // 链接
                eType = mySend ? ChatHolderType.VIEW_FROM_LINK : ChatHolderType.VIEW_TO_LINK;
                break;
            case XmppMessage.TYPE_SHARE_LINK: // 分享进来的链接
                eType = mySend ? ChatHolderType.VIEW_FROM_LINK_SHARE : ChatHolderType.VIEW_TO_LINK_SHARE;
                break;
            case XmppMessage.TYPE_IMAGE_TEXT:
                eType = mySend ? ChatHolderType.VIEW_FROM_IMAGE_TEXT : ChatHolderType.VIEW_TO_IMAGE_TEXT;
                break;
            case XmppMessage.TYPE_IMAGE_TEXT_MANY:
                eType = mySend ? ChatHolderType.VIEW_FROM_IMAGE_TEXT_MANY : ChatHolderType.VIEW_TO_IMAGE_TEXT_MANY;
                break;
            case XmppMessage.TYPE_END_CONNECT_VIDEO:
            case XmppMessage.TYPE_END_CONNECT_VOICE:
            case XmppMessage.TYPE_NO_CONNECT_VOICE:
            case XmppMessage.TYPE_NO_CONNECT_VIDEO:
            case XmppMessage.TYPE_IS_MU_CONNECT_VOICE:
            case XmppMessage.TYPE_IS_MU_CONNECT_Video:
                eType = mySend ? ChatHolderType.VIEW_FROM_MEDIA_CALL : ChatHolderType.VIEW_TO_MEDIA_CALL;
                break;
            case XmppMessage.TYPE_SHAKE: // 戳一戳
                eType = mySend ? ChatHolderType.VIEW_FROM_SHAKE : ChatHolderType.VIEW_TO_SHAKE;
                break;
            case XmppMessage.TYPE_CHAT_HISTORY: // 聊天记录
                eType = mySend ? ChatHolderType.VIEW_FROM_CHAT_HISTORY : ChatHolderType.VIEW_TO_CHAT_HISTORY;
                break;
            default:
                eType = ChatHolderType.VIEW_SYSTEM_TIP;
                break;
        }
        return eType;
    }

    public enum ChatHolderType {
        // 提示消息和 直播间消息
        VIEW_SYSTEM_TIP,
        VIEW_SYSTEM_LIVE,
        // 文字消息
        VIEW_FROM_TEXT,
        VIEW_TO_TEXT,
        // 回复消息，
        VIEW_FROM_REPLAY,
        VIEW_TO_REPLAY,
        // 图片消息
        VIEW_FROM_IMAGE,
        VIEW_TO_IMAGE,
        // 语音消息
        VIEW_FROM_VOICE,
        VIEW_TO_VOICE,
        // 视频消息
        VIEW_FROM_VIDEO,
        VIEW_TO_VIDEO,
        // gif消息
        VIEW_FROM_GIF,
        VIEW_TO_GIF,
        // 位置消息
        VIEW_FROM_LOCATION,
        VIEW_TO_LOCATION,
        // 文件消息
        VIEW_FROM_FILE,
        VIEW_TO_FILE,
        // 名片消息
        VIEW_FROM_CARD,
        VIEW_TO_CARD,
        // 红包消息(普通)
        VIEW_FROM_RED,
        VIEW_TO_RED,
        // 红包消息(口令)
        VIEW_FROM_RED_KEY,
        VIEW_TO_RED_KEY,
        // 转账消息
        VIEW_FROM_TRANSFER,
        VIEW_TO_TRANSFER,
        // 链接消息 （卡片）
        VIEW_FROM_LINK,
        VIEW_TO_LINK,
        // 链接消息（第三方分享）
        VIEW_FROM_LINK_SHARE,
        VIEW_TO_LINK_SHARE,
        // 图文消息（单条）
        VIEW_FROM_IMAGE_TEXT,
        VIEW_TO_IMAGE_TEXT,
        // 图文消息（多条）
        VIEW_FROM_IMAGE_TEXT_MANY,
        VIEW_TO_IMAGE_TEXT_MANY,
        // 语音邀请挂断消息
        VIEW_FROM_MEDIA_CALL,
        VIEW_TO_MEDIA_CALL,
        // 抖一抖消息
        VIEW_FROM_SHAKE,
        VIEW_TO_SHAKE,
        // 历史纪录消息
        VIEW_FROM_CHAT_HISTORY,
        VIEW_TO_CHAT_HISTORY
    }
}
