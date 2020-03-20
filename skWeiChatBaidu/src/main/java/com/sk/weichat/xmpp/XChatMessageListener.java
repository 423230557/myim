package com.sk.weichat.xmpp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sk.weichat.AppConfig;
import com.sk.weichat.MyApplication;
import com.sk.weichat.R;
import com.sk.weichat.Reporter;
import com.sk.weichat.adapter.MessageContactEvent;
import com.sk.weichat.adapter.MessageEventHongdian;
import com.sk.weichat.audio.NoticeVoicePlayer;
import com.sk.weichat.bean.CodePay;
import com.sk.weichat.bean.Contact;
import com.sk.weichat.bean.EventLoginStatus;
import com.sk.weichat.bean.EventNewNotice;
import com.sk.weichat.bean.Friend;
import com.sk.weichat.bean.MyZan;
import com.sk.weichat.bean.RoomMember;
import com.sk.weichat.bean.User;
import com.sk.weichat.bean.message.ChatMessage;
import com.sk.weichat.bean.message.NewFriendMessage;
import com.sk.weichat.bean.message.XmppMessage;
import com.sk.weichat.broadcast.CardcastUiUpdateUtil;
import com.sk.weichat.broadcast.MsgBroadcast;
import com.sk.weichat.broadcast.MucgroupUpdateUtil;
import com.sk.weichat.db.InternationalizationHelper;
import com.sk.weichat.db.dao.ChatMessageDao;
import com.sk.weichat.db.dao.ContactDao;
import com.sk.weichat.db.dao.FriendDao;
import com.sk.weichat.db.dao.MyZanDao;
import com.sk.weichat.db.dao.NewFriendDao;
import com.sk.weichat.db.dao.RoomMemberDao;
import com.sk.weichat.db.dao.login.MachineDao;
import com.sk.weichat.fragment.MessageFragment;
import com.sk.weichat.helper.FriendHelper;
import com.sk.weichat.pay.EventPaymentSuccess;
import com.sk.weichat.pay.EventReceiptSuccess;
import com.sk.weichat.ui.base.CoreManager;
import com.sk.weichat.ui.circle.MessageEventNotifyDynamic;
import com.sk.weichat.ui.message.ChatActivity;
import com.sk.weichat.ui.mucfile.XfileUtils;
import com.sk.weichat.util.Constants;
import com.sk.weichat.util.DES;
import com.sk.weichat.util.Md5Util;
import com.sk.weichat.util.PreferenceUtils;
import com.sk.weichat.util.TimeUtils;
import com.sk.weichat.xmpp.listener.ChatMessageListener;

import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.IncomingChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.json.JSONException;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.util.XmppStringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import de.greenrobot.event.EventBus;

import static android.content.Context.VIBRATOR_SERVICE;
import static com.sk.weichat.db.InternationalizationHelper.getString;
import static com.sk.weichat.xmpp.listener.ChatMessageListener.MESSAGE_SEND_SUCCESS;

/**
 * Created by Administrator on 2017/11/24.
 */

public class XChatMessageListener implements IncomingChatMessageListener {
    private CoreService mService;

    private String mLoginUserId;
    private Map<String, String> mMsgIDMap = new HashMap<>();// 因为多点登录 转发 问题，先用一个简单的方法去重

    public XChatMessageListener(CoreService service) {
        mService = service;
        mLoginUserId = CoreManager.requireSelf(service).getUserId();
        mMsgIDMap = new HashMap<>();
    }

    private Context getContext() {
        return mService;
    }

    @Override
    public void newIncomingMessage(EntityBareJid fromxx, Message message, Chat chat) {
        // body外
        String from = XmppStringUtils.parseBareJid(message.getFrom().toString());
        String resource = message.getFrom().toString().substring(message.getFrom().toString().indexOf("/") + 1, message.getFrom().length());// 发送方的resource ex:ios 、pc...
        String packetID = message.getPacketID();
        // body内(即我们发送的ChatMessage)
        String body = message.getBody();
        ChatMessage chatMessage = new ChatMessage(body);
        String fromUserId = chatMessage.getFromUserId();
        String toUserId = chatMessage.getToUserId();

        if (TextUtils.isEmpty(chatMessage.getPacketId())) {
            chatMessage.setPacketId(packetID);
        }
        chatMessage.setFromId(message.getFrom().toString());
        chatMessage.setToId(message.getTo().toString());
        Log.e("msg", "from:" + message.getFrom() + " ,to:" + message.getTo());
        Log.e("msg", "fromUserId:" + fromUserId + " ,toUserId:" + toUserId);

        if (mMsgIDMap.containsKey(chatMessage.getPacketId())) {
            return;
        }
        if (mMsgIDMap.size() > 20) {
            mMsgIDMap.clear();
        }
        mMsgIDMap.put(chatMessage.getPacketId(), chatMessage.getPacketId());

        int type = chatMessage.getType();
        if (type == 0) { // 消息过滤
            return;
        }
        decryptDES(chatMessage); // 解密
        Log.e("msg", message.getBody());

        if (chatMessage.getType() == XmppMessage.TYPE_SEND_ONLINE_STATUS) {// 收到协议为200消息
            moreLogin(resource, chatMessage);
            return;
        }

        boolean isGroupLiveNewFriendType = false;
        if (chatMessage.getType() >= XmppMessage.TYPE_MUCFILE_ADD
                && chatMessage.getType() <= XmppMessage.TYPE_GROUP_SEND_CARD) {// 这些消息类型的fromUserId都有可能等于toUserId
            isGroupLiveNewFriendType = true;
        }

        // 收到 我的设备 消息
        if (fromUserId.equals(toUserId) && !isGroupLiveNewFriendType) {
            if (!resource.equals("android") && message.getTo().toString().substring(message.getTo().toString().indexOf("/") + 1, message.getTo().length()).equals("android")
            ) {
                // from resource 不等于自己
                // to   resource 等于自己 方可进入该方法
                if (chatMessage.getType() == 26) {
                    String packetId = chatMessage.getContent();
                    ChatMessageDao.getInstance().updateMessageRead(mLoginUserId, resource, packetId, true);
                    boolean isReadChange = ChatMessageDao.getInstance().updateReadMessage(mLoginUserId, resource, packetId);
                    Intent intent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putString("packetId", packetId);
                    bundle.putBoolean("isReadChange", isReadChange);
                    intent.setAction(com.sk.weichat.broadcast.OtherBroadcast.IsRead);
                    intent.putExtras(bundle);
                    mService.sendBroadcast(intent);
                    return;
                }

                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, resource, chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, resource, chatMessage, false);
                }
            }
            return;
        }

        boolean isForwarding = false;// 该条消息是否需要转发
        boolean isNeedChangeMsgTableSave = false;// 该条消息是否需要换表存储
        // 注：非多点登录，不会走进该判断
        if (from.contains(mLoginUserId)) {
            // 收到转发消息，重置定时器
            MachineDao.getInstance().updateMachineOnLineStatus(resource, true);

            // 1.消息创建方的转发消息
            // 2.消息接收方的转发消息
            if (fromUserId.equals(mLoginUserId)) {
                isNeedChangeMsgTableSave = true;
                Log.e("msg", "消息创建方的转发消息,将isNeedChangeMsgTableSave置为true");
                chatMessage.setMySend(true);
                chatMessage.setUpload(true);
                chatMessage.setUploadSchedule(100);
                chatMessage.setMessageState(MESSAGE_SEND_SUCCESS);
                if (ChatMessageDao.getInstance().hasSameMessage(mLoginUserId, toUserId, packetID)) {
                    Log.e("msg", "table exist this msg，return");
                    return;
                }

            } else {
                Log.e("msg", "消息接收方的转发消息");
                if (ChatMessageDao.getInstance().hasSameMessage(mLoginUserId, fromUserId, packetID)) {
                    Log.e("msg", "table exist this msg，return");
                    return;
                }
            }
            Log.e("msg", "table not exist this msg，carry on");
        } else {// 收到对方发过来的消息
            isForwarding = true;
            Log.e("msg", "收到对方发过来的消息,将isForwarding状态置为true");

            Friend friend = FriendDao.getInstance().getFriend(mLoginUserId, chatMessage.getFromUserId());
            if (friend != null && friend.getOfflineNoPushMsg() == 0) {
                mService.notificationMessage(chatMessage, false);// 调用本地通知
            }
        }

        // 朋友圈相关消息 301-304
        if (type >= XmppMessage.DIANZAN && type <= XmppMessage.ATMESEE) {
            chatDiscover(body, chatMessage);
            // 朋友圈消息也要提示，
            NoticeVoicePlayer.getInstance().start();
            return;
        }

        // 新朋友相关消息 500-514
        if (type >= XmppMessage.TYPE_SAYHELLO && type <= XmppMessage.TYPE_BACK_DELETE) {
            chatFriend(body, chatMessage);
            return;
        }

        // 群文件上传、下载、删除 401-403
        if (type >= XmppMessage.TYPE_MUCFILE_ADD && type <= XmppMessage.TYPE_MUCFILE_DOWN) {
            Friend friend = FriendDao.getInstance().getFriend(mLoginUserId, chatMessage.getObjectId());
            chatGroupTipForMe(body, chatMessage, friend);
            return;
        }

        // 后台操作发送过来的xmpp
        if (type == XmppMessage.TYPE_DISABLE_GROUP) {
            if (chatMessage.getContent().equals("-1")) {// 锁定
                FriendDao.getInstance().updateFriendGroupStatus(mLoginUserId, chatMessage.getObjectId(), 3);// 更新本地群组状态
            } else if (chatMessage.getContent().equals("1")) {// 解锁
                FriendDao.getInstance().updateFriendGroupStatus(mLoginUserId, chatMessage.getObjectId(), 0);// 更新本地群组状态
            }
            mService.sendBroadcast(new Intent(MsgBroadcast.ACTION_DISABLE_GROUP_BY_SERVICE));
            return;
        }

        // 面对面建群有人加入、退出
        if (type == XmppMessage.TYPE_FACE_GROUP_NOTIFY) {
            MsgBroadcast.broadcastFaceGroupNotify(MyApplication.getContext(), "notify_list");
            return;
        }

        /*
        服务端已修改了发送群控制消息的逻辑(不再遍历群成员之后以单聊的方式发送，而是直接发送到群组内(但是部分协议还是会以单聊的方式发送给个人,ex:邀请群成员...)，
        但服务端代码还未上传(怕影响老版本用户)，所以我们在XMuChatMessageListener内的处理暂时还未用上，且该类中的逻辑不能删除，还需要添加判断本地是否有存在该条消息
        防止服务端代码上传后 单、群聊监听都收到同一条消息重复处理
         */
        if ((type >= XmppMessage.TYPE_CHANGE_NICK_NAME && type <= XmppMessage.NEW_MEMBER)
                || type == XmppMessage.TYPE_SEND_MANAGER
                || type == XmppMessage.TYPE_UPDATE_ROLE) {
            Friend friend = FriendDao.getInstance().getFriend(mLoginUserId, chatMessage.getObjectId());
            if (ChatMessageDao.getInstance().hasSameMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage.getPacketId())) {
                // 本地已经保存了这条消息，不处理，因为有些协议群组内也发了，单聊也发了
                Log.e("msg", "Return 6");
                return;
            }
            if (friend != null || type == XmppMessage.NEW_MEMBER) {
                if (chatMessage.getFromUserId().equals(mLoginUserId)) {
                    chatGroupTipFromMe(body, chatMessage, friend);
                } else {
                    chatGroupTipForMe(body, chatMessage, friend);
                }
            }
            return;
        }

        // 群组控制消息[2] 915-925
        if (type >= XmppMessage.TYPE_CHANGE_SHOW_READ && type <= XmppMessage.TYPE_GROUP_TRANSFER) {
            boolean isJumpOver = false;
            if (type == XmppMessage.TYPE_GROUP_VERIFY) {// 群验证跳过判断，因为自己发送的群验证消息object为一个json
                isJumpOver = true;
            }
            if (!isJumpOver && ChatMessageDao.getInstance().hasSameMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage.getPacketId())) {
                // 本地已经保存了这条消息，不处理，因为有些协议群组内也发了，单聊也发了
                Log.e("msg", "Return 7");
                return;
            }
            JSONObject jsonObject = JSONObject.parseObject(body);
            String toUserName = jsonObject.getString("toUserName");
            chatGroupTip2(type, chatMessage, toUserName);
            return;
        }

        // 表示这是已读回执类型的消息
        if (chatMessage.getType() == XmppMessage.TYPE_READ) {
            if (MyApplication.IS_SUPPORT_MULTI_LOGIN && isForwarding) {
                mService.sendChatMessage(mLoginUserId, chatMessage);
            }
            String packetId = chatMessage.getContent();

            if (chatMessage.getFromUserId().equals(mLoginUserId)) {// 其他端发送过来的已读
                ChatMessage msgById = ChatMessageDao.getInstance().findMsgById(mLoginUserId, chatMessage.getToUserId(), packetId);
                if (msgById != null && msgById.getIsReadDel()) {// 在其他端已读了该条阅后即焚消息，本端也需要删除
                    if (ChatMessageDao.getInstance().deleteSingleChatMessage(mLoginUserId, chatMessage.getToUserId(), packetId)) {
                        Intent intent = new Intent();
                        Bundle bundle = new Bundle();
                        bundle.putString("MULTI_LOGIN_READ_DELETE_PACKET", packetId);
                        intent.setAction(com.sk.weichat.broadcast.OtherBroadcast.MULTI_LOGIN_READ_DELETE);
                        intent.putExtras(bundle);
                        mService.sendBroadcast(intent);
                    }
                }
            } else {
                ChatMessageDao.getInstance().updateMessageRead(mLoginUserId, fromUserId, packetId, true);// 更新状态为已读
                boolean isReadChange = ChatMessageDao.getInstance().updateReadMessage(mLoginUserId, fromUserId, packetId);
                // 发送广播通知聊天页面，将未读的消息修改为已读
                Intent intent = new Intent();
                Bundle bundle = new Bundle();
                bundle.putString("packetId", packetId);
                bundle.putBoolean("isReadChange", isReadChange);
                intent.setAction(com.sk.weichat.broadcast.OtherBroadcast.IsRead);
                intent.putExtras(bundle);
                mService.sendBroadcast(intent);
            }
            return;
        }

        if (type == XmppMessage.TYPE_INPUT) {
            Intent intent = new Intent();
            intent.putExtra("fromId", chatMessage.getFromUserId());
            intent.setAction(com.sk.weichat.broadcast.OtherBroadcast.TYPE_INPUT);
            mService.sendBroadcast(intent);
            return;
        }

        // 某个用户发过来的撤回消息
        if (type == XmppMessage.TYPE_BACK) {
            if (MyApplication.IS_SUPPORT_MULTI_LOGIN && isForwarding) {
                mService.sendChatMessage(mLoginUserId, chatMessage);
            }
            backMessage(chatMessage);
            return;
        }

        // 某个成员领取了红包
        if (type == XmppMessage.TYPE_83) {
            if (MyApplication.IS_SUPPORT_MULTI_LOGIN && isForwarding) {
                mService.sendChatMessage(mLoginUserId, chatMessage);
            }

            String fromName;
            String toName;
            if (fromUserId.equals(mLoginUserId)) {
                fromName = MyApplication.getContext().getString(R.string.you);
                toName = MyApplication.getContext().getString(R.string.self);
            } else {
                fromName = chatMessage.getFromUserName();
                toName = MyApplication.getContext().getString(R.string.you);
            }

            String hasBennReceived = "";
            if (chatMessage.getFileSize() == 1) {// 红包是否领完
                try {
                    String sRedSendTime = chatMessage.getFilePath();
                    long redSendTime = Long.parseLong(sRedSendTime);
                    long betweenTime = chatMessage.getTimeSend() - redSendTime;
                    String sBetweenTime;
                    if (betweenTime < TimeUnit.MINUTES.toSeconds(1)) {
                        sBetweenTime = betweenTime + MyApplication.getContext().getString(R.string.second);
                    } else if (betweenTime < TimeUnit.HOURS.toSeconds(1)) {
                        sBetweenTime = TimeUnit.SECONDS.toMinutes(betweenTime) + MyApplication.getContext().getString(R.string.minute);
                    } else {
                        sBetweenTime = TimeUnit.SECONDS.toHours(betweenTime) + MyApplication.getContext().getString(R.string.hour);
                    }
                    hasBennReceived = MyApplication.getContext().getString(R.string.red_packet_has_received_place_holder, sBetweenTime);
                } catch (Exception e) {
                    hasBennReceived = MyApplication.getContext().getString(R.string.red_packet_has_received);
                }
            }
            String str = MyApplication.getContext().getString(R.string.tip_receive_red_packet_place_holder, fromName, toName) + hasBennReceived;

            // 针对红包领取的提示消息 需要做点击事件处理，将红包的type与id存入其他字段内
            chatMessage.setFileSize(XmppMessage.TYPE_83);
            chatMessage.setFilePath(chatMessage.getContent());

            chatMessage.setType(XmppMessage.TYPE_TIP);
            chatMessage.setContent(str);
            if (!TextUtils.isEmpty(chatMessage.getObjectId())) {// 群组红包领取 通知到群组
                fromUserId = chatMessage.getObjectId();
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, fromUserId, chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, fromUserId, chatMessage, true);
                }
            } else {// 单聊红包领取
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, fromUserId, chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, fromUserId, chatMessage, false);
                }
            }
            return;
        }

        // 红包退回通知，
        if (type == XmppMessage.TYPE_RED_BACK) {
            if (MyApplication.IS_SUPPORT_MULTI_LOGIN && isForwarding) {
                mService.sendChatMessage(mLoginUserId, chatMessage);
            }

            String str = MyApplication.getContext().getString(R.string.tip_red_back);
            chatMessage.setType(XmppMessage.TYPE_TIP);
            chatMessage.setContent(str);
            if (!TextUtils.isEmpty(chatMessage.getObjectId())) {// 群组红包退回 通知到群组
                fromUserId = chatMessage.getObjectId();
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, fromUserId, chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, fromUserId, chatMessage, true);
                }
            } else {// 单聊红包退回
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, fromUserId, chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, fromUserId, chatMessage, false);
                }
            }
            return;
        }

        if (type == XmppMessage.TYPE_TRANSFER_RECEIVE) {
            if (MyApplication.IS_SUPPORT_MULTI_LOGIN && isForwarding) {
                mService.sendChatMessage(mLoginUserId, chatMessage);
            }
            String str;
            if (type == XmppMessage.TYPE_TRANSFER_RECEIVE) {
                str = MyApplication.getContext().getString(R.string.transfer_received);
            } else {
                str = MyApplication.getContext().getString(R.string.transfer_backed);
            }
            chatMessage.setType(XmppMessage.TYPE_TIP);
            chatMessage.setContent(str);
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, fromUserId, chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, fromUserId, chatMessage, false);
            }
            return;
        }

        // 转账、收付款消息
        if (type >= XmppMessage.TYPE_TRANSFER_BACK && type <= XmppMessage.TYPE_RECEIPT_GET) {
            if (MyApplication.IS_SUPPORT_MULTI_LOGIN && isForwarding) {
                mService.sendChatMessage(mLoginUserId, chatMessage);
            }
            if (type == XmppMessage.TYPE_PAYMENT_OUT) {// 通知到付款界面
                CodePay codePay = JSON.parseObject(chatMessage.getContent(), CodePay.class);
                EventBus.getDefault().post(new EventPaymentSuccess(codePay.getToUserName()));
            } else if (type == XmppMessage.TYPE_RECEIPT_GET) {// 通知到收款界面
                CodePay codePay = JSON.parseObject(chatMessage.getContent(), CodePay.class);
                EventBus.getDefault().post(new EventReceiptSuccess(codePay.getToUserName()));
            }
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, fromUserId, chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, fromUserId, chatMessage, false);
            }
            return;
        }

        if (type == XmppMessage.TYPE_SCREENSHOT) {
            chatMessage.setType(XmppMessage.TYPE_TIP);
            chatMessage.setContent(mService.getString(R.string.tip_remote_screenshot));
            // 处理成tip后不return，正常流程处理，
        }

        // 存储消息
        if (chatMessage.isExpired()) {// 该条消息为过期消息(基本可以判断为离线消息)，不进行存库通知
            Log.e("msg", "该条消息为过期消息(基本可以判断为离线消息)，不进行存库通知");
            return;
        }

        // 戳一戳
        if (type == XmppMessage.TYPE_SHAKE) {
            Vibrator vibrator = (Vibrator) MyApplication.getContext().getSystemService(VIBRATOR_SERVICE);
            long[] pattern = {100, 400, 100, 400};
            vibrator.vibrate(pattern, -1);
        }

        Friend friend = FriendDao.getInstance().getFriend(mLoginUserId, chatMessage.getFromUserId());
        if (friend != null) {
            Log.e("msg", "朋友发送过来的消息");
            if (friend.getStatus() != -1) {
                saveCurrentMessage(chatMessage, isForwarding, isNeedChangeMsgTableSave);
                if (friend.getOfflineNoPushMsg() == 0) {// 未开启消息免打扰 可通知
                    if (!chatMessage.getFromUserId().equals(MyApplication.IsRingId)
                            && isForwarding) {// 收到该消息时不处于与发送方的聊天界面 && 非转发消息
                        Log.e("msg", "铃声通知");
                        if (!MessageFragment.foreground) {
                            // 消息页面不响铃，
                            NoticeVoicePlayer.getInstance().start();
                        }
                    }
                } else {
                    Log.e("msg", "已针对该好友开启了消息免打扰，不通知");
                }
            }
        } else {
            Log.e("msg", "陌生人发过来的消息");
            FriendDao.getInstance().createNewFriend(chatMessage);
            saveCurrentMessage(chatMessage, isForwarding, isNeedChangeMsgTableSave);
        }
    }

    private void backMessage(ChatMessage chatMessage) {
        // 本地数据库处理
        String packetId = chatMessage.getContent();
        if (TextUtils.isEmpty(packetId)) {
            return;
        }
        if (chatMessage.getFromUserId().equals(mLoginUserId)) {// 其他端撤回
            ChatMessageDao.getInstance().updateMessageBack(mLoginUserId, chatMessage.getToUserId(), packetId, MyApplication.getContext().getString(R.string.you));
        } else {
            ChatMessageDao.getInstance().updateMessageBack(mLoginUserId, chatMessage.getFromUserId(), packetId, chatMessage.getFromUserName());
        }

        /** 更新聊天界面 */
        Intent intent = new Intent();
        intent.putExtra("packetId", packetId);
        intent.setAction(com.sk.weichat.broadcast.OtherBroadcast.MSG_BACK);
        mService.sendBroadcast(intent);

        // 更新UI界面
        if (chatMessage.getFromUserId().equals(mLoginUserId)) {
            ChatMessage chat = ChatMessageDao.getInstance().getLastChatMessage(mLoginUserId, chatMessage.getToUserId());
            if (chat.getPacketId().equals(packetId)) {
                // 要撤回的消息正是朋友表的最后一条消息
                FriendDao.getInstance().updateFriendContent(mLoginUserId, chatMessage.getToUserId(),
                        MyApplication.getContext().getString(R.string.you) + " " + getString("JX_OtherWithdraw"), XmppMessage.TYPE_TEXT, chat.getTimeSend());
                MsgBroadcast.broadcastMsgUiUpdate(MyApplication.getInstance());
            }
        } else {
            ChatMessage chat = ChatMessageDao.getInstance().getLastChatMessage(mLoginUserId, chatMessage.getFromUserId());
            if (chat.getPacketId().equals(packetId)) {
                // 要撤回的消息正是朋友表的最后一条消息
                FriendDao.getInstance().updateFriendContent(mLoginUserId, chatMessage.getFromUserId(),
                        chatMessage.getFromUserName() + " " + getString("JX_OtherWithdraw"), XmppMessage.TYPE_TEXT, chat.getTimeSend());
                MsgBroadcast.broadcastMsgUiUpdate(MyApplication.getInstance());
            }
        }
    }

    private void saveCurrentMessage(ChatMessage chatMessage, boolean isForwarding, boolean isNeedChangeMsgTableSave) {
        if (isNeedChangeMsgTableSave) {
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, chatMessage.getToUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, chatMessage.getFromUserId(), chatMessage, false);
            }
        } else {
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, chatMessage.getFromUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, chatMessage.getFromUserId(), chatMessage, false);
            }
        }

        if (MyApplication.IS_SUPPORT_MULTI_LOGIN && isForwarding) {
            Log.e("msg", "消息存入数据库后，将消息转发出去");
            mService.sendChatMessage(mLoginUserId, chatMessage);
        }
    }

    private void chatGroupTipFromMe(String body, ChatMessage chatMessage, Friend friend) {
        JSONObject jsonObject = JSONObject.parseObject(body);
        String toUserId = jsonObject.getString("toUserId");
        String toUserName = jsonObject.getString("toUserName");

        // 我针对其他人的操作，只需要为toUserName重新赋值
        String xT = getName(friend, toUserId);
        if (!TextUtils.isEmpty(xT)) {
            toUserName = xT;
        }

        chatMessage.setGroup(false);
        switch (chatMessage.getType()) {
            case XmppMessage.TYPE_CHANGE_NICK_NAME:
                // 我修改了群内昵称
                String content = chatMessage.getContent();
                if (!TextUtils.isEmpty(content)) {
                    friend.setRoomMyNickName(content);
                    FriendDao.getInstance().updateRoomMyNickName(friend.getUserId(), content);
                    ListenerManager.getInstance().notifyNickNameChanged(friend.getUserId(), mLoginUserId, content);
                    ChatMessageDao.getInstance().updateNickName(mLoginUserId, friend.getUserId(), mLoginUserId, content);

                    chatMessage.setContent(chatMessage.getFromUserName() + " " + getString("JXMessageObject_UpdateNickName") + "‘" + content + "’");
                    chatMessage.setType(XmppMessage.TYPE_TIP);
                    if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                        ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
                    }
                }
                break;
            case XmppMessage.TYPE_CHANGE_ROOM_NAME:
                // 更新朋友表
                String groupName = chatMessage.getContent();
                FriendDao.getInstance().updateMucFriendRoomName(friend.getUserId(), groupName);
                ListenerManager.getInstance().notifyNickNameChanged(friend.getUserId(), "ROOMNAMECHANGE", groupName);

                chatMessage.setContent(chatMessage.getFromUserName() + " " + getString("JXMessageObject_UpdateRoomName") + groupName);
                chatMessage.setType(XmppMessage.TYPE_TIP);
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
                }
                break;
            case XmppMessage.TYPE_DELETE_ROOM:
                // 我解散了该群组
                mService.exitMucChat(chatMessage.getObjectId());
                FriendDao.getInstance().deleteFriend(mLoginUserId, chatMessage.getObjectId());
                // 消息表中删除
                ChatMessageDao.getInstance().deleteMessageTable(mLoginUserId, chatMessage.getObjectId());
                RoomMemberDao.getInstance().deleteRoomMemberTable(chatMessage.getObjectId());
                // 通知界面更新
                MsgBroadcast.broadcastMsgNumReset(mService);
                MsgBroadcast.broadcastMsgUiUpdate(MyApplication.getInstance());
                MucgroupUpdateUtil.broadcastUpdateUi(mService);
                break;
            case XmppMessage.TYPE_DELETE_MEMBER:
                if (toUserId.equals(mLoginUserId)) {
                    // 我退出了该群组
                    mService.exitMucChat(chatMessage.getObjectId());
                    FriendDao.getInstance().deleteFriend(mLoginUserId, chatMessage.getObjectId());
                    // 消息表中删除
                    ChatMessageDao.getInstance().deleteMessageTable(mLoginUserId, chatMessage.getObjectId());
                    RoomMemberDao.getInstance().deleteRoomMemberTable(chatMessage.getObjectId());
                    // 通知界面更新
                    MsgBroadcast.broadcastMsgNumReset(mService);
                    MsgBroadcast.broadcastMsgUiUpdate(MyApplication.getInstance());
                    MucgroupUpdateUtil.broadcastUpdateUi(mService);
                } else {
                    // toUserId被我踢出群组
                    chatMessage.setContent(toUserName + " " + getString("KICKED_OUT_GROUP"));
                    chatMessage.setType(XmppMessage.TYPE_TIP);
                    // chatMessage.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));
                    if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                        ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
                    }
                }
                break;
            case XmppMessage.TYPE_NEW_NOTICE:
                // 我发布了公告
                EventBus.getDefault().post(new EventNewNotice(chatMessage));
                String notice = chatMessage.getContent();
                chatMessage.setContent(chatMessage.getFromUserName() + " " + getString("JXMessageObject_AddNewAdv") + notice);
                chatMessage.setType(XmppMessage.TYPE_TIP);
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
                }
                break;
            case XmppMessage.TYPE_GAG:
                // 我对群组内其他成员禁言了
                long time = Long.parseLong(chatMessage.getContent());
                // 为防止其他用户接收不及时，给3s的误差
                if (time > (System.currentTimeMillis() / 1000) + 3) {
                    String formatTime = XfileUtils.fromatTime((time * 1000), "MM-dd HH:mm");
                    // message.setContent("用户：" + toUserName + " 已被禁言到" + formatTime);
                    chatMessage.setContent(chatMessage.getFromUserName() + " " + getString("JXMessageObject_Yes") + toUserName +
                            getString("JXMessageObject_SetGagWithTime") + formatTime);
                } else {
                    // message.setContent("用户：" + toUserName + " 已被取消禁言");
                    /*chatMessage.setContent(chatMessage.getFromUserName() + " " + getString("JXMessageObject_Yes") + toUserName +
                            getString("JXMessageObject_CancelGag"));*/
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.be_cancel_ban_place_holder, toUserName, chatMessage.getFromUserName()));
                }

                chatMessage.setType(XmppMessage.TYPE_TIP);
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
                }
                break;
            case XmppMessage.NEW_MEMBER:
                String desc = chatMessage.getFromUserName() + " " + InternationalizationHelper.getString("JXMessageObject_GroupChat");
                if (toUserId.equals(mLoginUserId)) {
                    /**
                     * 以下四种情况会进入该判断内
                     * 1.我在本端创建了该群组
                     * 2.我在本端加入该群组
                     * 3.我在其他端创建了该群组
                     * 4.我在其他端加入了该群组
                     * 5.面对面建群，调加入接口服务端代发了907
                     *
                     * 注：以上四种情况服务端都会通过smack推送Type==907的消息过来
                     */
                    Friend mFriend = FriendDao.getInstance().getFriend(mLoginUserId, chatMessage.getObjectId());
                    if (mFriend != null && mFriend.getGroupStatus() == 1) {// 本地存在该群组，且被踢出了该群组 先将该群组删除在创建(如调用updateGroupStatus直接修改该群组状态，可以会有问题，保险起见还是创建吧)
                        FriendDao.getInstance().deleteFriend(mLoginUserId, friend.getUserId());
                        ChatMessageDao.getInstance().deleteMessageTable(mLoginUserId, friend.getUserId());
                        mFriend = null;
                    }

                    // 将当前群组部分属性存入共享参数内
                    String roomId = "";
                    try {
                        roomId = jsonObject.getString("fileName");
                        String other = jsonObject.getString("other");
                        JSONObject jsonObject2 = JSONObject.parseObject(other);
                        int showRead = jsonObject2.getInteger("showRead");
                        int allowSecretlyChat = jsonObject2.getInteger("allowSendCard");
                        MyApplication.getInstance().saveGroupPartStatus(chatMessage.getObjectId(), showRead, allowSecretlyChat,
                                1, 1, 0);
                    } catch (Exception e) {
                        Log.e("msg", "解析时抛出异常");
                    }

                    /**
                     * 本来只需要判断mFriend是否为空即可，但在第一、二种情况下，在调用接口之后(创建、加入接口)本地也在创建该群组，而当前收到Type==907后
                     * 又会去创建群组，造成群组重复的问题，所以需要坐下兼容
                     */
                    if (mFriend == null
                            && !chatMessage.getObjectId().equals(MyApplication.mRoomKeyLastCreate)) {
                        Friend mCreateFriend = new Friend();
                        mCreateFriend.setOwnerId(mLoginUserId);
                        mCreateFriend.setUserId(chatMessage.getObjectId());
                        mCreateFriend.setNickName(chatMessage.getContent());
                        mCreateFriend.setDescription("");
                        mCreateFriend.setRoomId(roomId);
                        mCreateFriend.setContent(desc);
                        mCreateFriend.setTimeSend(chatMessage.getTimeSend());
                        mCreateFriend.setRoomFlag(1);
                        mCreateFriend.setStatus(Friend.STATUS_FRIEND);
                        mCreateFriend.setGroupStatus(0);
                        FriendDao.getInstance().createOrUpdateFriend(mCreateFriend);

                        // 调用smack加入群组的方法
                        mService.joinMucChat(chatMessage.getObjectId(), 0);
                        MsgBroadcast.broadcastFaceGroupNotify(MyApplication.getContext(), "join_room");
                    }
                } else {
                    // toUserId被我邀请进入群组
                    desc = chatMessage.getFromUserName() + " " + getString("JXMessageObject_InterFriend") + toUserName;

                    String roomId = jsonObject.getString("fileName");
                    operatingRoomMemberDao(0, roomId, chatMessage.getToUserId(), toUserName);
                }

                chatMessage.setType(XmppMessage.TYPE_TIP);
                chatMessage.setContent(desc);
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, chatMessage.getObjectId(), chatMessage, true);
                    MsgBroadcast.broadcastMsgRoomUpdateGetRoomStatus(MyApplication.getInstance());
                }
                break;
            case XmppMessage.TYPE_SEND_MANAGER:
                // 我对该群组成员设置/取消管理员
                String messageType = chatMessage.getContent();
                if (messageType.equals("1")) {
                    chatMessage.setContent(chatMessage.getFromUserName() + " " + InternationalizationHelper.getString("JXSettingVC_Set") + toUserName + " " + InternationalizationHelper.getString("JXMessage_admin"));
                } else {
                    chatMessage.setContent(chatMessage.getFromUserName() + " " + InternationalizationHelper.getString("JXSip_Canceled") + toUserName + " " + InternationalizationHelper.getString("JXMessage_admin"));
                }
                chatMessage.setType(XmppMessage.TYPE_TIP);
                chatMessage.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
                }
                break;
            case XmppMessage.TYPE_UPDATE_ROLE:
                // 我对该群组成员设置/取消管理员
                int tipContent = -1;
                switch (chatMessage.getContent()) {
                    case "1": // 1:设置隐身人
                        tipContent = R.string.tip_set_invisible_place_holder;
                        break;
                    case "-1": // -1:取消隐身人
                        tipContent = R.string.tip_cancel_invisible_place_holder;
                        break;
                    case "2": // 2：设置监控人
                        tipContent = R.string.tip_set_guardian_place_holder;
                        break;
                    case "0": // 0：取消监控人
                        tipContent = R.string.tip_cancel_guardian_place_holder;
                        break;
                    default:
                        Reporter.unreachable();
                        return;
                }
                chatMessage.setContent(getContext().getString(tipContent, chatMessage.getFromUserName(), toUserName));
                chatMessage.setType(XmppMessage.TYPE_TIP);
                chatMessage.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
                }
                break;

        }
    }

    /**
     * 因为多点登录问题，我在其他端做的群组操作，本端也收到了，需要做一样的处理
     * 所以在做处理前需要判断该群组操作是否为自己操作的(fromUserId==当前账号id)
     * 如果为自己操作了走到了另外一个方法，所以本方法内的对自己的操作代码可以注释掉
     * 现在先不管了，反正不会走到对自己的判断内，无影响
     */
    private void chatGroupTipForMe(String body, ChatMessage chatMessage, Friend friend) {
        int type = chatMessage.getType();
        String fromUserId = chatMessage.getFromUserId();
        String fromUserName = chatMessage.getFromUserName();
        JSONObject jsonObject = JSONObject.parseObject(body);
        String toUserId = jsonObject.getString("toUserId");
        String toUserName = jsonObject.getString("toUserName");

        if (!TextUtils.isEmpty(toUserId)) {
            if (toUserId.equals(mLoginUserId)) {// 其他人针对我的操作，只需要为fromUserName赋值
                String xF = getName(friend, fromUserId);
                if (!TextUtils.isEmpty(xF)) {
                    fromUserName = xF;
                }
            } else {// 其他人针对其他人的操作，fromUserName与toUserName都需要赋值
                String xF = getName(friend, fromUserId);
                if (!TextUtils.isEmpty(xF)) {
                    fromUserName = xF;
                }
                String xT = getName(friend, toUserId);
                if (!TextUtils.isEmpty(xT)) {
                    toUserName = xT;
                }
            }
        }

        chatMessage.setGroup(false);
        if (type == XmppMessage.TYPE_CHANGE_NICK_NAME) {
            // 修改群内昵称
            String content = chatMessage.getContent();
            if (!TextUtils.isEmpty(toUserId) && toUserId.equals(mLoginUserId)) {
                // 我修改了昵称
                if (!TextUtils.isEmpty(content)) {
                    friend.setRoomMyNickName(content);
                    FriendDao.getInstance().updateRoomMyNickName(friend.getUserId(), content);
                    ListenerManager.getInstance().notifyNickNameChanged(friend.getUserId(), toUserId, content);
                    ChatMessageDao.getInstance().updateNickName(mLoginUserId, friend.getUserId(), toUserId, content);
                }
            } else {
                // 其他人修改了昵称，通知下就可以了
                chatMessage.setContent(fromUserName + " " + getString("JXMessageObject_UpdateNickName") + "‘" + content + "’");
                chatMessage.setType(XmppMessage.TYPE_TIP);
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
                }
                ListenerManager.getInstance().notifyNickNameChanged(friend.getUserId(), toUserId, content);
                ChatMessageDao.getInstance().updateNickName(mLoginUserId, friend.getUserId(), toUserId, content);
            }
        } else if (type == XmppMessage.TYPE_CHANGE_ROOM_NAME) {
            // 修改房间名
            // 更新朋友表
            String content = chatMessage.getContent();
            FriendDao.getInstance().updateMucFriendRoomName(friend.getUserId(), content);
            ListenerManager.getInstance().notifyNickNameChanged(friend.getUserId(), "ROOMNAMECHANGE", content);

            chatMessage.setContent(toUserName + " " + getString("JXMessageObject_UpdateRoomName") + content);
            chatMessage.setType(XmppMessage.TYPE_TIP);
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
            }
        } else if (type == XmppMessage.TYPE_DELETE_ROOM) {
            // 群主解散该群
            if (fromUserId.equals(toUserId)) {
                // 我为群主
                FriendDao.getInstance().deleteFriend(mLoginUserId, chatMessage.getObjectId());
                // 消息表中删除
                ChatMessageDao.getInstance().deleteMessageTable(mLoginUserId, chatMessage.getObjectId());
                RoomMemberDao.getInstance().deleteRoomMemberTable(chatMessage.getObjectId());
                // 通知界面更新
                MsgBroadcast.broadcastMsgNumReset(mService);
                MsgBroadcast.broadcastMsgUiUpdate(MyApplication.getInstance());
                MucgroupUpdateUtil.broadcastUpdateUi(mService);
            } else {
                mService.exitMucChat(chatMessage.getObjectId());
                // 2 标志该群已被解散  更新朋友表
                FriendDao.getInstance().updateFriendGroupStatus(mLoginUserId, friend.getUserId(), 2);
                chatMessage.setType(XmppMessage.TYPE_TIP);
                chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_disbanded));
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, chatMessage.getObjectId(), chatMessage, true);
                }
            }
            ListenerManager.getInstance().notifyDeleteMucRoom(chatMessage.getObjectId());
        } else if (type == XmppMessage.TYPE_DELETE_MEMBER) {
            // 群组 退出 || 踢人
            chatMessage.setType(XmppMessage.TYPE_TIP);
            // 退出 || 被踢出群组
            if (toUserId.equals(mLoginUserId)) { // 该操作为针对我的
                if (fromUserId.equals(toUserId)) {
                    // 自己退出了群组
                    mService.exitMucChat(friend.getUserId());
                    // 删除这个房间
                    FriendDao.getInstance().deleteFriend(mLoginUserId, friend.getUserId());
                    RoomMemberDao.getInstance().deleteRoomMemberTable(chatMessage.getObjectId());
                    // 消息表中删除
                    ChatMessageDao.getInstance().deleteMessageTable(mLoginUserId, friend.getUserId());
                    // 通知界面更新
                    MsgBroadcast.broadcastMsgNumReset(mService);
                    MsgBroadcast.broadcastMsgUiUpdate(MyApplication.getInstance());
                    MucgroupUpdateUtil.broadcastUpdateUi(mService);
                } else {
                    // 被xx踢出了群组
                    mService.exitMucChat(friend.getUserId());
                    // / 1 标志被踢出该群组， 更新朋友表
                    FriendDao.getInstance().updateFriendGroupStatus(mLoginUserId, friend.getUserId(), 1);
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_been_kick_place_holder, fromUserName));

                    ListenerManager.getInstance().notifyMyBeDelete(friend.getUserId());// 通知群组聊天界面
                }
            } else {
                // 其他人退出 || 被踢出
                if (fromUserId.equals(toUserId)) {
                    // message.setContent(toUserName + "退出了群组");
                    chatMessage.setContent(toUserName + " " + getString("QUIT_GROUP"));
                } else {
                    // message.setContent(toUserName + "被踢出群组");
                    chatMessage.setContent(toUserName + " " + getString("KICKED_OUT_GROUP"));
                }
                // 更新RoomMemberDao、更新群聊界面
                operatingRoomMemberDao(1, friend.getRoomId(), toUserId, null);
                MsgBroadcast.broadcastMsgRoomUpdateGetRoomStatus(MyApplication.getContext());
            }

            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
            }
        } else if (type == XmppMessage.TYPE_NEW_NOTICE) {
            // 发布公告
            EventBus.getDefault().post(new EventNewNotice(chatMessage));
            String content = chatMessage.getContent();
            chatMessage.setContent(fromUserName + " " + getString("JXMessageObject_AddNewAdv") + content);
            chatMessage.setType(XmppMessage.TYPE_TIP);
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
            }
        } else if (type == XmppMessage.TYPE_GAG) {// 群组与直播间禁言
            long time = Long.parseLong(chatMessage.getContent());
            if (toUserId != null && toUserId.equals(mLoginUserId)) {
                // 被禁言了|| 取消禁言 更新RoomTalkTime字段
                FriendDao.getInstance().updateRoomTalkTime(mLoginUserId, friend.getUserId(), (int) time);
                ListenerManager.getInstance().notifyMyVoiceBanned(friend.getUserId(), (int) time);
            }

            // 为防止其他用户接收不及时，给3s的误差
            if (time > (System.currentTimeMillis() / 1000) + 3) {
                String formatTime = XfileUtils.fromatTime((time * 1000), "MM-dd HH:mm");
                // message.setContent("用户：" + toUserName + " 已被禁言到" + formatTime);
                chatMessage.setContent(fromUserName + " " + getString("JXMessageObject_Yes") + toUserName +
                        getString("JXMessageObject_SetGagWithTime") + formatTime);
            } else {
                // message.setContent("用户：" + toUserName + " 已被取消禁言");
                /*chatMessage.setContent(chatMessage.getFromUserName() + " " + getString("JXMessageObject_Yes") + toUserName +
                        getString("JXMessageObject_CancelGag"));*/
                chatMessage.setContent(toUserName + MyApplication.getContext().getString(R.string.tip_been_cancel_ban_place_holder, fromUserName));
            }

            chatMessage.setType(XmppMessage.TYPE_TIP);
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
            }
        } else if (type == XmppMessage.NEW_MEMBER) {
            String desc = "";
            if (chatMessage.getFromUserId().equals(toUserId)) {
                // 主动加入
                desc = fromUserName + " " + getString("JXMessageObject_GroupChat");
            } else {
                // 被邀请加入
                desc = fromUserName + " " + getString("JXMessageObject_InterFriend") + toUserName;

                String roomId = jsonObject.getString("fileName");
                if (!toUserId.equals(mLoginUserId)) {// 被邀请人为自己时不能更新RoomMemberDao，如更新了，在群聊界面判断出该表有人而不会在去调用接口获取该群真实的人数了
                    operatingRoomMemberDao(0, roomId, chatMessage.getToUserId(), toUserName);
                }
            }

            if (toUserId.equals(mLoginUserId)) {
                // 其他人邀请我加入该群组 才会进入该方法
                if (friend != null && friend.getGroupStatus() == 1) {// 本地存在该群组，且被踢出了该群组 先将该群组删除在创建(如调用updateGroupStatus直接修改该群组状态，可以会有问题，保险起见还是创建吧)
                    FriendDao.getInstance().deleteFriend(mLoginUserId, friend.getUserId());
                    ChatMessageDao.getInstance().deleteMessageTable(mLoginUserId, friend.getUserId());
                }

                String roomId = "";
                // 将当前群组部分属性存入共享参数内
                try {
                    // 群已读、公开、群验证、群成员列表可见、允许普通成员私聊
                    roomId = jsonObject.getString("fileName");
                    String other = jsonObject.getString("other");
                    JSONObject jsonObject2 = JSONObject.parseObject(other);
                    int showRead = jsonObject2.getInteger("showRead");
                    int allowSecretlyChat = jsonObject2.getInteger("allowSendCard");
                    MyApplication.getInstance().saveGroupPartStatus(chatMessage.getObjectId(), showRead, allowSecretlyChat,
                            1, 1, 0);
                } catch (Exception e) {
                    Log.e("msg", "解析时抛出异常");
                }

                Friend mCreateFriend = new Friend();
                mCreateFriend.setOwnerId(mLoginUserId);
                mCreateFriend.setUserId(chatMessage.getObjectId());
                mCreateFriend.setNickName(chatMessage.getContent());
                mCreateFriend.setDescription("");
                mCreateFriend.setRoomId(roomId);
                mCreateFriend.setContent(desc);
                mCreateFriend.setTimeSend(chatMessage.getTimeSend());
                mCreateFriend.setRoomFlag(1);
                mCreateFriend.setStatus(Friend.STATUS_FRIEND);
                mCreateFriend.setGroupStatus(0);
                FriendDao.getInstance().createOrUpdateFriend(mCreateFriend);
                // 调用smack加入群组的方法
                // 被邀请加入群组，lastSeconds == 当前时间 - 被邀请时的时间
                mService.joinMucChat(chatMessage.getObjectId(), 0);
            }

            // 更新数据库
            chatMessage.setType(XmppMessage.TYPE_TIP);
            chatMessage.setContent(desc);
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, chatMessage.getObjectId(), chatMessage, true);
                MsgBroadcast.broadcastMsgRoomUpdateGetRoomStatus(MyApplication.getContext());
            }
        } else if (type == XmppMessage.TYPE_SEND_MANAGER) {
            String content = chatMessage.getContent();
            int role;
            if (content.equals("1")) {
                role = 2;
                chatMessage.setContent(fromUserName + " " + InternationalizationHelper.getString("JXSettingVC_Set") + toUserName + " " + InternationalizationHelper.getString("JXMessage_admin"));
            } else {
                role = 3;
                chatMessage.setContent(fromUserName + " " + InternationalizationHelper.getString("JXSip_Canceled") + toUserName + " " + InternationalizationHelper.getString("JXMessage_admin"));
            }

            RoomMemberDao.getInstance().updateRoomMemberRole(friend.getRoomId(), toUserId, role);

            chatMessage.setType(XmppMessage.TYPE_TIP);
            // chatMessage.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
                Intent intent = new Intent();
                intent.putExtra("roomId", friend.getUserId());
                intent.putExtra("toUserId", chatMessage.getToUserId());
                intent.putExtra("isSet", content.equals("1"));
                intent.setAction(com.sk.weichat.broadcast.OtherBroadcast.REFRESH_MANAGER);
                mService.sendBroadcast(intent);
            }
        } else if (type == XmppMessage.TYPE_UPDATE_ROLE) {
            int tipContent = -1;
            int role = RoomMember.ROLE_MEMBER;
            switch (chatMessage.getContent()) {
                case "1": // 1:设置隐身人
                    tipContent = R.string.tip_set_invisible_place_holder;
                    role = RoomMember.ROLE_INVISIBLE;
                    break;
                case "-1": // -1:取消隐身人
                    tipContent = R.string.tip_cancel_invisible_place_holder;
                    break;
                case "2": // 2：设置监控人
                    tipContent = R.string.tip_set_guardian_place_holder;
                    role = RoomMember.ROLE_GUARDIAN;
                    break;
                case "0": // 0：取消监控人
                    tipContent = R.string.tip_cancel_guardian_place_holder;
                    break;
                default:
                    Reporter.unreachable();
                    return;
            }
            chatMessage.setContent(getContext().getString(tipContent, chatMessage.getFromUserName(), toUserName));

            RoomMemberDao.getInstance().updateRoomMemberRole(friend.getRoomId(), toUserId, role);

            chatMessage.setType(XmppMessage.TYPE_TIP);
            // chatMessage.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, friend.getUserId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, friend.getUserId(), chatMessage, true);
                MsgBroadcast.broadcastMsgRoleChanged(getContext());
            }
        }

        // 某个用户删除了群文件 或者 上传了群文件
        if (type == XmppMessage.TYPE_MUCFILE_DEL || type == XmppMessage.TYPE_MUCFILE_ADD) {
            String roomid = chatMessage.getObjectId();
            String str;
            if (type == XmppMessage.TYPE_MUCFILE_DEL) {
                // str = chatMessage.getFromUserName() + " 删除了群文件 " + chatMessage.getFilePath();
                str = fromUserName + " " + getString("JXMessage_fileDelete") + ":" + chatMessage.getFilePath();
            } else {
                // str = chatMessage.getFromUserName() + " 上传了群文件 " + chatMessage.getFilePath();
                str = fromUserName + " " + getString("JXMessage_fileUpload") + ":" + chatMessage.getFilePath();
            }
            // 更新朋友表最后一条消息
            FriendDao.getInstance().updateFriendContent(mLoginUserId, roomid, str, type, TimeUtils.sk_time_current_time());
            FriendDao.getInstance().markUserMessageUnRead(mLoginUserId, roomid); // 加一个小红点
            // 更新聊天记录表最后一条消息
            chatMessage.setContent(str);
            chatMessage.setType(XmppMessage.TYPE_TIP);
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, roomid, chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, roomid, chatMessage, true);
            }
            // 更新消息界面
            MsgBroadcast.broadcastMsgUiUpdate(MyApplication.getInstance());
            return;
        }
    }

    // 更新群成员表
    private void operatingRoomMemberDao(int type, String roomId, String userId, String userName) {
        if (type == 0) {
            RoomMember roomMember = new RoomMember();
            roomMember.setRoomId(roomId);
            roomMember.setUserId(userId);
            roomMember.setUserName(userName);
            roomMember.setCardName(userName);
            roomMember.setRole(3);
            roomMember.setCreateTime(0);
            RoomMemberDao.getInstance().saveSingleRoomMember(roomId, roomMember);
        } else {
            RoomMemberDao.getInstance().deleteRoomMember(roomId, userId);
        }
    }

    private void chatGroupTip2(int type, ChatMessage chatMessage, String toUserName) {
        chatMessage.setType(XmppMessage.TYPE_TIP);
        if (type == XmppMessage.TYPE_GROUP_VERIFY) {
            // 916协议分为两种
            // 第一种为服务端发送，触发条件为群主在群组信息内 开/关 进群验证按钮，群组内每个人都能收到
            // 第二种为邀请、申请加入该群组，由邀请人或加入方发送给群主的消息，只有群主可以收到
            if (!TextUtils.isEmpty(chatMessage.getContent()) &&
                    (chatMessage.getContent().equals("0") || chatMessage.getContent().equals("1"))) {// 第一种
                if (chatMessage.getContent().equals("1")) {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_group_enable_verify));
                } else {
                    chatMessage.setContent(mService.getString(R.string.tip_group_disable_verify));
                }
                // chatMessage.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));
                if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage)) {
                    ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, chatMessage.getObjectId(), chatMessage, true);
                }
            } else {//  群聊邀请确认消息 我收到该条消息 说明我就是该群的群主 待我审核
                try {
                    org.json.JSONObject json = new org.json.JSONObject(chatMessage.getObjectId());
                    String isInvite = json.getString("isInvite");
                    if (TextUtils.isEmpty(isInvite)) {
                        isInvite = "0";
                    }
                    if (isInvite.equals("0")) {
                        String id = json.getString("userIds");
                        String[] ids = id.split(",");
                        chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_invite_need_verify_place_holder, chatMessage.getFromUserName(), ids.length));
                    } else {
                        chatMessage.setContent(chatMessage.getFromUserName() + MyApplication.getContext().getString(R.string.tip_need_verify_place_holder));
                    }
                    String roomJid = json.getString("roomJid");
                    if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, roomJid, chatMessage)) {
                        ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, roomJid, chatMessage, true);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        } else {
            if (type == XmppMessage.TYPE_CHANGE_SHOW_READ) {
                PreferenceUtils.putBoolean(MyApplication.getContext(),
                        Constants.IS_SHOW_READ + chatMessage.getObjectId(), chatMessage.getContent().equals("1"));
                if (chatMessage.getContent().equals("1")) {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_read));
                } else {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_read));
                }
            } else if (type == XmppMessage.TYPE_GROUP_LOOK) {
                if (chatMessage.getContent().equals("1")) {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_private));
                } else {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_public));
                }
            } else if (type == XmppMessage.TYPE_GROUP_SHOW_MEMBER) {
                if (chatMessage.getContent().equals("1")) {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_member));
                } else {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_member));
                }
            } else if (type == XmppMessage.TYPE_GROUP_SEND_CARD) {
                PreferenceUtils.putBoolean(MyApplication.getContext(),
                        Constants.IS_SEND_CARD + chatMessage.getObjectId(), chatMessage.getContent().equals("1"));
                if (chatMessage.getContent().equals("1")) {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_chat_privately));
                } else {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_chat_privately));
                }
            } else if (type == XmppMessage.TYPE_GROUP_ALL_SHAT_UP) {
                PreferenceUtils.putBoolean(MyApplication.getContext(),
                        Constants.GROUP_ALL_SHUP_UP + chatMessage.getObjectId(), !chatMessage.getContent().equals("0"));
                if (!chatMessage.getContent().equals("0")) {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_now_ban_all));
                } else {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_now_disable_ban_all));
                }
                MsgBroadcast.broadcastMsgRoomUpdateGetRoomStatus(MyApplication.getContext());
            } else if (type == XmppMessage.TYPE_GROUP_ALLOW_NORMAL_INVITE) {
                if (!chatMessage.getContent().equals("0")) {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_invite));
                } else {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_invite));
                }
            } else if (type == XmppMessage.TYPE_GROUP_ALLOW_NORMAL_UPLOAD) {
                if (!chatMessage.getContent().equals("0")) {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_upload));
                } else {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_upload));
                }
            } else if (type == XmppMessage.TYPE_GROUP_ALLOW_NORMAL_CONFERENCE) {
                PreferenceUtils.putBoolean(MyApplication.getContext(),
                        Constants.IS_ALLOW_NORMAL_CONFERENCE + chatMessage.getObjectId(), !chatMessage.getContent().equals("0"));
                if (!chatMessage.getContent().equals("0")) {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_meeting));
                } else {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_meeting));
                }
            } else if (type == XmppMessage.TYPE_GROUP_ALLOW_NORMAL_SEND_COURSE) {
                PreferenceUtils.putBoolean(MyApplication.getContext(),
                        Constants.IS_ALLOW_NORMAL_SEND_COURSE + chatMessage.getObjectId(), !chatMessage.getContent().equals("0"));
                if (!chatMessage.getContent().equals("0")) {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_enable_cource));
                } else {
                    chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_owner_disable_cource));
                }
            } else if (type == XmppMessage.TYPE_GROUP_TRANSFER) {
                chatMessage.setContent(MyApplication.getContext().getString(R.string.tip_new_group_owner_place_holder, toUserName));
                Friend friend = FriendDao.getInstance().getFriend(mLoginUserId, chatMessage.getObjectId());
                if (friend != null) {
                    FriendDao.getInstance().updateRoomCreateUserId(mLoginUserId,
                            chatMessage.getObjectId(), chatMessage.getToUserId());
                    RoomMemberDao.getInstance().updateRoomMemberRole(friend.getRoomId(), chatMessage.getToUserId(), 1);
                }
            }
            if (ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, chatMessage.getObjectId(), chatMessage)) {
                ListenerManager.getInstance().notifyNewMesssage(mLoginUserId, chatMessage.getObjectId(), chatMessage, true);
            }
        }
    }


    /**
     * 朋友圈相关消息逻辑处理
     */
    private void chatDiscover(String body, ChatMessage chatMessage) {
        if (MyZanDao.getInstance().hasSameZan(chatMessage.getPacketId())) {
            Log.e("msg", "本地已存在该条赞或评论消息");
            return;
        }
        MyZan zan = new MyZan();
        zan.setFromUserId(chatMessage.getFromUserId());
        zan.setFromUsername(chatMessage.getFromUserName());
        zan.setSendtime(String.valueOf(chatMessage.getTimeSend()));
        zan.setLoginUserId(mLoginUserId);
        zan.setZanbooleanyidu(0);
        zan.setSystemid(chatMessage.getPacketId());
        /**
         * object组成: id,type,content
         *
         * id
         * type:1 文本 2 图片 3 语音 4 视频
         * content:文本内容
         */
        String[] data = chatMessage.getObjectId().split(",");
        zan.setCricleuserid(data[0]);
        zan.setType(Integer.parseInt(data[1]));
        if (Integer.parseInt(data[1]) == 1) {// 文本类型
            zan.setContent(data[2]);
        } else {// 其他类型
            zan.setContenturl(data[2]);
        }

        if (chatMessage.getType() == XmppMessage.DIANZAN) {// 赞
            zan.setHuifu("101");
            if (MyZanDao.getInstance().addZan(zan)) {
                int size = MyZanDao.getInstance().getZanSize(mLoginUserId);
                EventBus.getDefault().post(new MessageEventHongdian(size));
                EventBus.getDefault().post(new MessageEventNotifyDynamic(size));
            }
        } else if (chatMessage.getType() == XmppMessage.PINGLUN) {// 评论
            if (chatMessage.getContent() != null) {
                zan.setHuifu(chatMessage.getContent());
            }
            JSONObject jsonObject = JSONObject.parseObject(body);
            String toUserName = jsonObject.getString("toUserName");
            if (!TextUtils.isEmpty(toUserName)) {
                zan.setTousername(toUserName);
            }
            MyZanDao.getInstance().addZan(zan);
            int size = MyZanDao.getInstance().getZanSize(mLoginUserId);
            EventBus.getDefault().post(new MessageEventHongdian(size));
            EventBus.getDefault().post(new MessageEventNotifyDynamic(size));
        } else if (chatMessage.getType() == XmppMessage.ATMESEE) {// 提醒我看
            zan.setHuifu("102");
            MyZanDao.getInstance().addZan(zan);
            int size = MyZanDao.getInstance().getZanSize(mLoginUserId);
            EventBus.getDefault().post(new MessageEventHongdian(size));
            EventBus.getDefault().post(new MessageEventNotifyDynamic(size));
        }
    }

    /**
     * 朋友相关消息逻辑处理
     */
    private void chatFriend(String body, ChatMessage chatMessage) {
        /**
         * 改变
         * 1.因为 新的朋友从消息页面移至通讯录，不需要显示最后一条消息，updateLastChatMessage(...,Friend.ID_NEW_FRIEND_MESSAGE,...)可以注释掉
         * 2.因为多点登录，ex:我在web端删除了某个好友，android端接收到了，需要另外做处理
         */
        Log.e("msg", mLoginUserId + "，" + chatMessage.getFromUserId() + "，" + chatMessage.getToUserId());
        Log.e("msg", chatMessage.getType() + "，" + chatMessage.getPacketId());

        if (chatMessage.getFromUserId().equals(mLoginUserId)) {// 我在其他端做的操作，在android也接收到了
            chatFriendFromMe(body, chatMessage);
        } else {
            chatFriendForMe(body, chatMessage);
        }
    }

    /**
     * 自己在其他端做的好友操作，发送过来的新朋友消息，需要另做处理
     * 处理逻辑与发送该条Type消息时所做的操作一样，可将代码复制过来
     */
    private void chatFriendFromMe(String body, ChatMessage chatMessage) {
        String toUserId = chatMessage.getToUserId();
        JSONObject jsonObject = JSONObject.parseObject(body);
        String toUserName = jsonObject.getString("toUserName");
        if (TextUtils.isEmpty(toUserName)) {
            toUserName = "NULL";
        }
        switch (chatMessage.getType()) {
            case XmppMessage.TYPE_SAYHELLO:
                // 我与对方打招呼
                NewFriendMessage message = NewFriendMessage.createLocalMessage(CoreManager.requireSelf(mService),
                        XmppMessage.TYPE_SAYHELLO, InternationalizationHelper.getString("HEY-HELLO"), toUserId, toUserName);
                NewFriendDao.getInstance().createOrUpdateNewFriend(message);
                NewFriendDao.getInstance().changeNewFriendState(toUserId, Friend.STATUS_10);//朋友状态
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, message, true);

                // 发送打招呼的消息
                ChatMessage sayMessage = new ChatMessage();
                sayMessage.setFromUserId(mLoginUserId);
                sayMessage.setFromUserName(CoreManager.requireSelf(mService).getNickName());
                sayMessage.setContent(InternationalizationHelper.getString("HEY-HELLO"));
                sayMessage.setType(XmppMessage.TYPE_TEXT); //文本类型
                sayMessage.setMySend(true);
                sayMessage.setMessageState(ChatMessageListener.MESSAGE_SEND_SUCCESS);
                sayMessage.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));
                sayMessage.setTimeSend(TimeUtils.sk_time_current_time());
                ChatMessageDao.getInstance().saveNewSingleChatMessage(message.getOwnerId(), message.getUserId(), sayMessage);
                break;
            case XmppMessage.TYPE_PASS:
                // 我同意了对方的加好友请求
                NewFriendMessage passMessage = NewFriendMessage.createLocalMessage(CoreManager.requireSelf(mService),
                        XmppMessage.TYPE_PASS, null, toUserId, toUserName);

                NewFriendDao.getInstance().ascensionNewFriend(passMessage, Friend.STATUS_FRIEND);
                FriendHelper.addFriendExtraOperation(mLoginUserId, toUserId);
                FriendDao.getInstance().updateFriendContent(mLoginUserId, toUserId,
                        InternationalizationHelper.getString("JXMessageObject_BeFriendAndChat"), XmppMessage.TYPE_TEXT, TimeUtils.sk_time_current_time());
                NewFriendDao.getInstance().changeNewFriendState(toUserId, Friend.STATUS_12);
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, passMessage, true);
                break;
            case XmppMessage.TYPE_FEEDBACK:
                // 我发送给对方的回话
                NewFriendMessage feedBackMessage = NewFriendDao.getInstance().getNewFriendById(mLoginUserId, toUserId);
                if (feedBackMessage == null) {
                    feedBackMessage = NewFriendMessage.createLocalMessage(CoreManager.requireSelf(mService),
                            XmppMessage.TYPE_FEEDBACK, chatMessage.getContent(), toUserId, toUserName);
                    NewFriendDao.getInstance().createOrUpdateNewFriend(feedBackMessage);
                }
                if (feedBackMessage.getState() == Friend.STATUS_11 || feedBackMessage.getState() == Friend.STATUS_15) {
                    NewFriendDao.getInstance().changeNewFriendState(feedBackMessage.getUserId(), Friend.STATUS_15);
                } else {
                    NewFriendDao.getInstance().changeNewFriendState(feedBackMessage.getUserId(), Friend.STATUS_14);
                }
                NewFriendDao.getInstance().updateNewFriendContent(feedBackMessage.getUserId(), chatMessage.getContent());

                ChatMessage chatFeedMessage = new ChatMessage();// 本地也保存一份
                chatFeedMessage.setType(XmppMessage.TYPE_TEXT); // 文本类型
                chatFeedMessage.setFromUserId(mLoginUserId);
                chatFeedMessage.setFromUserName(CoreManager.requireSelf(mService).getNickName());
                chatFeedMessage.setContent(chatMessage.getContent());
                chatFeedMessage.setMySend(true);
                chatFeedMessage.setMessageState(ChatMessageListener.MESSAGE_SEND_SUCCESS);
                chatFeedMessage.setPacketId(UUID.randomUUID().toString().replaceAll("-", ""));
                chatFeedMessage.setTimeSend(TimeUtils.sk_time_current_time());
                ChatMessageDao.getInstance().saveNewSingleAnswerMessage(mLoginUserId, toUserId, chatFeedMessage);
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, feedBackMessage, true);
                break;
            case XmppMessage.TYPE_FRIEND:
                // 对方未开启验证，我直接将对方添加为好友
                NewFriendMessage friendMessage = NewFriendMessage.createLocalMessage(CoreManager.requireSelf(mService),
                        XmppMessage.TYPE_FRIEND, null, toUserId, toUserName);
                NewFriendDao.getInstance().ascensionNewFriend(friendMessage, Friend.STATUS_FRIEND);
                FriendHelper.addFriendExtraOperation(mLoginUserId, toUserId);
                NewFriendDao.getInstance().changeNewFriendState(toUserId, Friend.STATUS_22);
                FriendDao.getInstance().updateFriendContent(mLoginUserId, toUserId,
                        InternationalizationHelper.getString("JXMessageObject_BeFriendAndChat"), XmppMessage.TYPE_TEXT, TimeUtils.sk_time_current_time());
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, friendMessage, true);
                break;
            case XmppMessage.TYPE_BLACK:
                // 我将对方拉黑
                NewFriendMessage blackMessage = NewFriendMessage.createLocalMessage(CoreManager.requireSelf(mService),
                        XmppMessage.TYPE_BLACK, null, toUserId, toUserName);
                FriendDao.getInstance().updateFriendStatus(mLoginUserId, toUserId, Friend.STATUS_BLACKLIST);
                FriendHelper.addBlacklistExtraOperation(blackMessage.getOwnerId(), blackMessage.getUserId());
                NewFriendDao.getInstance().createOrUpdateNewFriend(blackMessage);
                NewFriendDao.getInstance().changeNewFriendState(toUserId, Friend.STATUS_18);
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, blackMessage, true);
                break;
            case XmppMessage.TYPE_REFUSED:
                // 我将对方移除黑名单
                NewFriendMessage removeMessage = NewFriendMessage.createLocalMessage(CoreManager.requireSelf(mService),
                        XmppMessage.TYPE_REFUSED, null, toUserId, toUserName);
                NewFriendDao.getInstance().ascensionNewFriend(removeMessage, Friend.STATUS_FRIEND);
                FriendHelper.beAddFriendExtraOperation(removeMessage.getOwnerId(), removeMessage.getUserId());
                NewFriendDao.getInstance().createOrUpdateNewFriend(removeMessage);
                NewFriendDao.getInstance().changeNewFriendState(toUserId, Friend.STATUS_24);
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, removeMessage, true);
                break;
            case XmppMessage.TYPE_DELALL:
                // 我删除了对方
                NewFriendMessage deleteMessage = NewFriendMessage.createLocalMessage(CoreManager.requireSelf(mService),
                        XmppMessage.TYPE_DELALL, null, chatMessage.getToUserId(), toUserName);
                FriendHelper.removeAttentionOrFriend(mLoginUserId, chatMessage.getToUserId());
                NewFriendDao.getInstance().createOrUpdateNewFriend(deleteMessage);
                NewFriendDao.getInstance().changeNewFriendState(chatMessage.getToUserId(), Friend.STATUS_16);
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, deleteMessage, true);
                break;
        }
        // 更新通讯录页面
        CardcastUiUpdateUtil.broadcastUpdateUi(mService);
    }

    /**
     * 对方发送过来的新朋友消息，处理逻辑不变
     */
    private void chatFriendForMe(String body, ChatMessage chatMessage) {
        //  收到对方的新朋友消息也需要转发
        if (MyApplication.IS_SUPPORT_MULTI_LOGIN) {
            mService.sendChatMessage(mLoginUserId, chatMessage);
        }
        // json:fromUserId fromUserName type  content timeSend
        NewFriendMessage mNewMessage = new NewFriendMessage(body);
        mNewMessage.setOwnerId(mLoginUserId);
        mNewMessage.setUserId(chatMessage.getFromUserId());
        mNewMessage.setRead(false);
        mNewMessage.setMySend(false);
        mNewMessage.setPacketId(chatMessage.getPacketId());
        String content = "";
        switch (chatMessage.getType()) {
            case XmppMessage.TYPE_SAYHELLO:
                // 对方发过来的打招呼消息
                NewFriendDao.getInstance().createOrUpdateNewFriend(mNewMessage);
                NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_11);
                // FriendDao.getInstance().updateLastChatMessage(mLoginUserId, Friend.ID_NEW_FRIEND_MESSAGE, chatMessage);

                ChatMessage sayHelloMessage = new ChatMessage();
                sayHelloMessage.setType(XmppMessage.TYPE_TEXT); //文本类型
                sayHelloMessage.setFromUserId(chatMessage.getFromUserId());
                sayHelloMessage.setFromUserName(chatMessage.getFromUserName());
                sayHelloMessage.setContent(InternationalizationHelper.getString("HEY-HELLO"));
                sayHelloMessage.setMySend(false);
                sayHelloMessage.setMessageState(ChatMessageListener.MESSAGE_SEND_SUCCESS);
                sayHelloMessage.setPacketId(chatMessage.getPacketId());
                sayHelloMessage.setTimeSend(chatMessage.getTimeSend());
                ChatMessageDao.getInstance().saveNewSingleChatMessage(mLoginUserId, chatMessage.getFromUserId(), sayHelloMessage);
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                break;
            case XmppMessage.TYPE_PASS:
                // 对方同意加我为好友
                NewFriendDao.getInstance().ascensionNewFriend(mNewMessage, Friend.STATUS_FRIEND);
                FriendHelper.beAddFriendExtraOperation(mNewMessage.getOwnerId(), mNewMessage.getUserId());
                /*content = getString("JXFriendObject_PassGo");
                FriendDao.getInstance().updateLastChatMessage(mLoginUserId, Friend.ID_NEW_FRIEND_MESSAGE, content);*/
                NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_13);//添加了xxx
                content = InternationalizationHelper.getString("JXMessageObject_BeFriendAndChat");
                FriendDao.getInstance().updateLastChatMessage(mLoginUserId, mNewMessage.getUserId(), content);
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                break;
            case XmppMessage.TYPE_FEEDBACK: {
                // 对方的回话
                NewFriendMessage feedBackMessage = NewFriendDao.getInstance().getNewFriendById(mNewMessage.getOwnerId(), mNewMessage.getUserId());
                NewFriendDao.getInstance().createOrUpdateNewFriend(mNewMessage);
                if (feedBackMessage.getState() == Friend.STATUS_11 || feedBackMessage.getState() == Friend.STATUS_15) {
                    NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_15);
                } else {
                    NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_14);
                }
                NewFriendDao.getInstance().updateNewFriendContent(mNewMessage.getUserId(), chatMessage.getContent());

                ChatMessage message = new ChatMessage();
                message.setType(XmppMessage.TYPE_TEXT);// 文本类型
                message.setFromUserId(mNewMessage.getUserId());
                message.setFromUserName(mNewMessage.getNickName());
                message.setContent(mNewMessage.getContent());
                message.setMySend(false);
                message.setMessageState(ChatMessageListener.MESSAGE_SEND_SUCCESS);
                message.setPacketId(chatMessage.getPacketId());
                message.setTimeSend(TimeUtils.sk_time_current_time());
                ChatMessageDao.getInstance().saveNewSingleAnswerMessage(mLoginUserId, mNewMessage.getUserId(), message);
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                break;
            }
            case XmppMessage.TYPE_FRIEND:
                // 我未开启好友验证，对方直接添加我为好友
                NewFriendDao.getInstance().ascensionNewFriend(mNewMessage, Friend.STATUS_FRIEND);
                FriendHelper.beAddFriendExtraOperation(mNewMessage.getOwnerId(), mNewMessage.getUserId());
                NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_21);//添加了xxx
                /*content = mNewMessage.getNickName() + " 添加我为好友";
                FriendDao.getInstance().updateLastChatMessage(mLoginUserId, Friend.ID_NEW_FRIEND_MESSAGE, content);*/
                content = InternationalizationHelper.getString("JXMessageObject_BeFriendAndChat");
                FriendDao.getInstance().updateLastChatMessage(mLoginUserId, mNewMessage.getUserId(), content);
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                break;
            case XmppMessage.TYPE_BLACK:
                // 对方将我拉黑
                NewFriendDao.getInstance().createOrUpdateNewFriend(mNewMessage);// 本地可能没有该NewFriend，需要先创建在修改其status
                NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_19);
                FriendHelper.beDeleteAllNewFriend(mNewMessage.getOwnerId(), mNewMessage.getUserId());
               /* content = mNewMessage.getNickName() + " " + getString("JXFriendObject_PulledBlack");
                FriendDao.getInstance().updateLastChatMessage(mLoginUserId, Friend.ID_NEW_FRIEND_MESSAGE, content);*/
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                // 关闭聊天界面
                ChatActivity.callFinish(getContext(), getContext().getString(R.string.be_pulled_black), mNewMessage.getUserId());
                break;
            case XmppMessage.TYPE_REFUSED:
                // 对方将我移出了黑名单
                NewFriendDao.getInstance().ascensionNewFriend(mNewMessage, Friend.STATUS_FRIEND);
                FriendHelper.beAddFriendExtraOperation(mNewMessage.getOwnerId(), mNewMessage.getUserId());
                NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_24);//添加了xxx
                /*content = mNewMessage.getNickName() + " 已取消黑名单";
                FriendDao.getInstance().updateLastChatMessage(mLoginUserId, Friend.ID_NEW_FRIEND_MESSAGE, content);*/
                content = getString("JXMessageObject_BeFriendAndChat");
                FriendDao.getInstance().updateLastChatMessage(mLoginUserId, mNewMessage.getUserId(), content);
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                break;
            case XmppMessage.TYPE_DELALL:
                // 对方删除了我
                NewFriendDao.getInstance().createOrUpdateNewFriend(mNewMessage);// 本地可能没有该NewFriend，需要先创建在修改其status
                FriendHelper.beDeleteAllNewFriend(mNewMessage.getOwnerId(), mNewMessage.getUserId());
                NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_17);
                /*content = mNewMessage.getNickName() + " 删除了我";
                FriendDao.getInstance().updateLastChatMessage(mLoginUserId, Friend.ID_NEW_FRIEND_MESSAGE, content);*/
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                // 关闭聊天界面
                ChatActivity.callFinish(getContext(), InternationalizationHelper.getString("JXAlert_DeleteFirend"), mNewMessage.getUserId());
                break;
            case XmppMessage.TYPE_CONTACT_BE_FRIEND:
                // 对方通过 手机联系人 添加我 直接成为好友
                NewFriendDao.getInstance().ascensionNewFriend(mNewMessage, Friend.STATUS_FRIEND);
                FriendHelper.beAddFriendExtraOperation(mNewMessage.getOwnerId(), mNewMessage.getUserId());
                NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_25);// 通过手机联系人添加
                content = InternationalizationHelper.getString("JXMessageObject_BeFriendAndChat");
                FriendDao.getInstance().updateLastChatMessage(mLoginUserId, mNewMessage.getUserId(), content);
                ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                break;
            case XmppMessage.TYPE_NEW_CONTACT_REGISTER: {
                // 我之前上传给服务端的联系人表内有人注册了，更新 手机联系人
                JSONObject jsonObject = JSONObject.parseObject(chatMessage.getContent());
                Contact contact = new Contact();
                contact.setTelephone(jsonObject.getString("telephone"));
                contact.setToTelephone(jsonObject.getString("toTelephone"));
                String toUserId = jsonObject.getString("toUserId");
                contact.setToUserId(toUserId);
                contact.setToUserName(jsonObject.getString("toUserName"));
                contact.setUserId(jsonObject.getString("userId"));
                if (ContactDao.getInstance().createContact(contact)) {// 本地创建成功 更新未读数量
                    EventBus.getDefault().post(new MessageContactEvent(toUserId));
                }
                break;
            }
            case XmppMessage.TYPE_REMOVE_ACCOUNT: {
                // 用户被后台删除，用于客户端更新本地数据 ，from是系统管理员 ObjectId是被删除人的userId，
                String removedAccountId = chatMessage.getObjectId();
                Friend toUser = FriendDao.getInstance().getFriend(mLoginUserId, removedAccountId);
                if (toUser != null) {
                    mNewMessage.setUserId(removedAccountId);
                    mNewMessage.setNickName(toUser.getNickName());
                    NewFriendDao.getInstance().createOrUpdateNewFriend(mNewMessage);// 本地可能没有该NewFriend，需要先创建在修改其status
                    FriendHelper.friendAccountRemoved(mNewMessage.getOwnerId(), mNewMessage.getUserId());
                    NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_26);
                    NewFriendDao.getInstance().updateNewFriendContent(mNewMessage.getUserId(), chatMessage.getContent());
                    ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                    // 关闭聊天界面
                    ChatActivity.callFinish(getContext(), chatMessage.getContent(), removedAccountId);
                }
                break;
            }
            case XmppMessage.TYPE_BACK_DELETE: {
                // 后台删除了我的一个好友关系，
                JSONObject json = JSON.parseObject(chatMessage.getObjectId());
                String fromUserId = json.getString("fromUserId");
                String fromUserName = json.getString("fromUserName");
                String toUserId = json.getString("toUserId");
                String toUserName = json.getString("toUserName");
                if (TextUtils.equals(fromUserId, mLoginUserId)) {
                    // 我删除别人，
                    mNewMessage.setUserId(toUserId);
                    mNewMessage.setNickName(toUserName);
                    NewFriendDao.getInstance().createOrUpdateNewFriend(mNewMessage);// 本地可能没有该NewFriend，需要先创建在修改其status
                    FriendHelper.beDeleteAllNewFriend(mNewMessage.getOwnerId(), mNewMessage.getUserId());
                    NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_16);
                    ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                } else {
                    // 别人删除我，
                    mNewMessage.setUserId(fromUserId);
                    mNewMessage.setNickName(fromUserName);
                    NewFriendDao.getInstance().createOrUpdateNewFriend(mNewMessage);// 本地可能没有该NewFriend，需要先创建在修改其status
                    FriendHelper.beDeleteAllNewFriend(mNewMessage.getOwnerId(), mNewMessage.getUserId());
                    NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_17);
                    ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                }
                // 关闭聊天界面
                ChatActivity.callFinish(getContext(), InternationalizationHelper.getString("JXAlert_DeleteFirend"), mNewMessage.getUserId());
                break;
            }
            case XmppMessage.TYPE_BACK_BLACK: {
                // 后台拉黑了我的好友或者拉黑了我本身，
                JSONObject json = JSON.parseObject(chatMessage.getObjectId());
                String fromUserId = json.getString("fromUserId");
                String fromUserName = json.getString("fromUserName");
                String toUserId = json.getString("toUserId");
                if (TextUtils.equals(fromUserId, mLoginUserId)) {
                    // 我拉黑别人，
                    mNewMessage.setUserId(toUserId);
                    Friend toUser = FriendDao.getInstance().getFriend(mLoginUserId, toUserId);
                    if (toUser == null) {
                        Reporter.post("后台拉黑了个不存在的好友，" + toUserId);
                        return;
                    }
                    mNewMessage.setNickName(toUser.getNickName());
                    FriendDao.getInstance().updateFriendStatus(mLoginUserId, toUserId, Friend.STATUS_BLACKLIST);
                    FriendHelper.addBlacklistExtraOperation(mLoginUserId, toUserId);

                    ChatMessage addBlackChatMessage = new ChatMessage();
                    addBlackChatMessage.setContent(InternationalizationHelper.getString("JXFriendObject_AddedBlackList") + " " + toUser.getShowName());
                    addBlackChatMessage.setTimeSend(TimeUtils.sk_time_current_time());
                    FriendDao.getInstance().updateLastChatMessage(mLoginUserId, Friend.ID_NEW_FRIEND_MESSAGE, addBlackChatMessage);

                    NewFriendDao.getInstance().createOrUpdateNewFriend(mNewMessage);
                    NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_18);
                    ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                    // 关闭聊天界面
                    ChatActivity.callFinish(getContext(), chatMessage.getContent(), toUserId);
                } else {
                    // 我被拉黑，
                    mNewMessage.setUserId(fromUserId);
                    mNewMessage.setNickName(fromUserName);
                    NewFriendDao.getInstance().createOrUpdateNewFriend(mNewMessage);// 本地可能没有该NewFriend，需要先创建在修改其status
                    NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_19);
                    FriendHelper.beDeleteAllNewFriend(mNewMessage.getOwnerId(), mNewMessage.getUserId());
               /* content = mNewMessage.getNickName() + " " + getString("JXFriendObject_PulledBlack");
                FriendDao.getInstance().updateLastChatMessage(mLoginUserId, Friend.ID_NEW_FRIEND_MESSAGE, content);*/
                    ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                    // 关闭聊天界面
                    ChatActivity.callFinish(getContext(), getContext().getString(R.string.be_pulled_black), mNewMessage.getUserId());
                }
                break;
            }
            case XmppMessage.TYPE_BACK_REFUSED: {
                // 后台取消拉黑了我的好友或者取消拉黑了我本身，
                JSONObject json = JSON.parseObject(chatMessage.getObjectId());
                String fromUserId = json.getString("fromUserId");
                String fromUserName = json.getString("fromUserName");
                String toUserId = json.getString("toUserId");
                if (TextUtils.equals(fromUserId, mLoginUserId)) {
                    // 取消拉黑了我的黑名单，
                    mNewMessage.setUserId(toUserId);
                    Friend toUser = FriendDao.getInstance().getFriend(mLoginUserId, toUserId);
                    if (toUser == null) {
                        Reporter.post("后台取消拉黑了个不存在的好友，" + toUserId);
                    } else {
                        mNewMessage.setNickName(toUser.getNickName());
                    }
                    NewFriendDao.getInstance().ascensionNewFriend(mNewMessage, Friend.STATUS_FRIEND);
                    FriendHelper.beAddFriendExtraOperation(mLoginUserId, toUserId);

                    User self = CoreManager.requireSelf(mService);
                    ChatMessage removeChatMessage = new ChatMessage();
                    removeChatMessage.setContent(self.getNickName() + InternationalizationHelper.getString("REMOVE"));
                    removeChatMessage.setTimeSend(TimeUtils.sk_time_current_time());
                    FriendDao.getInstance().updateLastChatMessage(mLoginUserId, Friend.ID_NEW_FRIEND_MESSAGE, removeChatMessage);
                    NewFriendDao.getInstance().createOrUpdateNewFriend(mNewMessage);
                    NewFriendDao.getInstance().changeNewFriendState(toUserId, Friend.STATUS_24);
                    ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                } else {
                    // 我被取消拉黑，
                    mNewMessage.setUserId(fromUserId);
                    mNewMessage.setNickName(fromUserName);
                    NewFriendDao.getInstance().ascensionNewFriend(mNewMessage, Friend.STATUS_FRIEND);
                    FriendHelper.beAddFriendExtraOperation(mNewMessage.getOwnerId(), mNewMessage.getUserId());
                    NewFriendDao.getInstance().changeNewFriendState(mNewMessage.getUserId(), Friend.STATUS_24);//添加了xxx
                    content = getString("JXMessageObject_BeFriendAndChat");
                    FriendDao.getInstance().updateLastChatMessage(mLoginUserId, mNewMessage.getUserId(), content);
                    ListenerManager.getInstance().notifyNewFriend(mLoginUserId, mNewMessage, true);
                }
                break;
            }
            case XmppMessage.TYPE_NEWSEE:// 对方单向关注了我
            case XmppMessage.TYPE_DELSEE:// 对方取消了对我的单向关注
                // 单向关注 功能已去掉
                break;
            case XmppMessage.TYPE_RECOMMEND:
                // 新推荐好友 好像无此功能
                break;
            default:
                break;
        }
        // 更新通讯录页面
        CardcastUiUpdateUtil.broadcastUpdateUi(mService);
    }

    private String getName(Friend friend, String userId) {
        if (friend == null) {
            return null;
        }
        RoomMember mRoomMember = RoomMemberDao.getInstance().getSingleRoomMember(friend.getRoomId(), mLoginUserId);
        if (mRoomMember != null && mRoomMember.getRole() == 1) {// 我为群主 Name显示为群内备注
            RoomMember member = RoomMemberDao.getInstance().getSingleRoomMember(friend.getRoomId(), userId);
            if (member != null && !TextUtils.equals(member.getUserName(), member.getCardName())) {
                // 当userName与cardName不一致时，我们认为群主有设置群内备注
                return member.getCardName();
            } else {
                Friend mFriend = FriendDao.getInstance().getFriend(mLoginUserId, userId);
                if (mFriend != null && !TextUtils.isEmpty(mFriend.getRemarkName())) {
                    return mFriend.getRemarkName();
                }
            }
        } else {// 为好友 显示备注
            Friend mFriend = FriendDao.getInstance().getFriend(mLoginUserId, userId);
            if (mFriend != null && !TextUtils.isEmpty(mFriend.getRemarkName())) {
                return mFriend.getRemarkName();
            }
        }
        return null;
    }

    /**
     * 解密
     */
    private void decryptDES(ChatMessage chatMessage) {
        int isEncrypt = chatMessage.getIsEncrypt();
        if (isEncrypt == 1) {
            try {
                String decryptKey = Md5Util.toMD5(AppConfig.apiKey + chatMessage.getTimeSend() + chatMessage.getPacketId());
                String decryptContent = DES.decryptDES(chatMessage.getContent(), decryptKey);
                // 为chatMessage重新设值
                chatMessage.setContent(decryptContent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // 多点登录
    private void moreLogin(String resource, ChatMessage chatMessage) {
        boolean onLine;
        if (chatMessage.getContent().equals("0")) {
            // 下线消息
            onLine = false;
        } else {
            // 上线消息
            onLine = true;
        }
        EventBus.getDefault().post(new EventLoginStatus(resource, onLine));
        MachineDao.getInstance().updateMachineOnLineStatus(resource, onLine);
    }
}
