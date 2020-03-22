package com.sk.weichat.ui.message.multi;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.sk.weichat.AppConstant;
import com.sk.weichat.MyApplication;
import com.sk.weichat.R;
import com.sk.weichat.Reporter;
import com.sk.weichat.bean.Friend;
import com.sk.weichat.bean.Report;
import com.sk.weichat.bean.RoomMember;
import com.sk.weichat.bean.message.MucRoom;
import com.sk.weichat.bean.message.MucRoom.Notice;
import com.sk.weichat.bean.message.MucRoomMember;
import com.sk.weichat.broadcast.MsgBroadcast;
import com.sk.weichat.broadcast.MucgroupUpdateUtil;
import com.sk.weichat.db.InternationalizationHelper;
import com.sk.weichat.db.dao.ChatMessageDao;
import com.sk.weichat.db.dao.FriendDao;
import com.sk.weichat.db.dao.RoomMemberDao;
import com.sk.weichat.helper.AvatarHelper;
import com.sk.weichat.helper.DialogHelper;
import com.sk.weichat.ui.MainActivity;
import com.sk.weichat.ui.base.BaseActivity;
import com.sk.weichat.ui.message.search.SearchChatHistoryActivity;
import com.sk.weichat.ui.mucfile.MucFileListActivity;
import com.sk.weichat.ui.other.BasicInfoActivity;
import com.sk.weichat.ui.other.QRcodeActivity;
import com.sk.weichat.util.AsyncUtils;
import com.sk.weichat.util.CameraUtil;
import com.sk.weichat.util.CharUtils;
import com.sk.weichat.util.Constants;
import com.sk.weichat.util.ExpandView;
import com.sk.weichat.util.LogUtils;
import com.sk.weichat.util.PreferenceUtils;
import com.sk.weichat.util.TimeUtils;
import com.sk.weichat.util.ToastUtil;
import com.sk.weichat.view.MsgSaveDaysDialog;
import com.sk.weichat.view.ReportDialog;
import com.sk.weichat.view.SelectionFrame;
import com.sk.weichat.view.TipDialog;
import com.sk.weichat.view.VerifyDialog;
import com.sk.weichat.volley.Result;
import com.sk.weichat.xmpp.ListenerManager;
import com.suke.widget.SwitchButton;
import com.xuan.xuanhttplibrary.okhttp.HttpUtils;
import com.xuan.xuanhttplibrary.okhttp.callback.BaseCallback;
import com.xuan.xuanhttplibrary.okhttp.result.ObjectResult;

import org.apache.http.Header;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.greenrobot.event.EventBus;
import de.greenrobot.event.Subscribe;
import de.greenrobot.event.ThreadMode;
import in.srain.cube.views.GridViewWithHeaderAndFooter;
import okhttp3.Call;

import static com.sk.weichat.broadcast.MsgBroadcast.ACTION_MSG_UPDATE_ROOM_INVITE;
import static com.sk.weichat.broadcast.MsgBroadcast.EXTRA_ENABLED;

/**
 * 群组信息
 */
public class RoomInfoActivity extends BaseActivity {
    private static final int RESULT_FOR_ADD_MEMBER = 1;
    private static final int RESULT_FOR_MODIFY_NOTICE = 5;
    private static final int REQUEST_CODE_CAPTURE_CROP_PHOTO = 4;
    private static final int REQUEST_CODE_PICK_CROP_PHOTO = 2;
    private static final int REQUEST_CODE_CROP_PHOTO = 3;
    MucRoom mucRoom;
    RefreshBroadcastReceiver receiver = new RefreshBroadcastReceiver();
    private String mRoomJid;
    private Friend mRoom;
    private Context mContext = RoomInfoActivity.this;
    // 是否从聊天界面进入
    private boolean isMucChatComing;
    private String mLoginUserId;
    private GridViewWithHeaderAndFooter mGridView;
    private GridViewAdapter mAdapter;
    private TextView mRoomNameTv;
    private TextView mRoomDescTv;
    private TextView mNoticeTv;
    private TextView mNickNameTv;
    private TextView romNameTv, romDesTv, gongGaoTv, myGroupName, shieldGroupMesTv, jinyanTv;
    private RelativeLayout room_qrcode;
    // 消息管理
    private SwitchButton mSbTopChat;
    private SwitchButton mSbDisturb;
    private SwitchButton mSbShield;
    // 全体禁言
    private SwitchButton mSbAllShutUp;
    private Button mBtnQuitRoom;
    private ImageView mExpandIv;
    private ExpandView mExpandView;
    private TextView mCreatorTv;
    private TextView buileTimetv;
    private TextView mCreateTime;
    private TextView numberTopTv;
    private TextView mCountTv;
    private TextView mCountTv2;
    private TextView mMsgSaveDays;
    MsgSaveDaysDialog.OnMsgSaveDaysDialogClickListener onMsgSaveDaysDialogClickListener = new MsgSaveDaysDialog.OnMsgSaveDaysDialogClickListener() {
        @Override
        public void tv1Click() {
            updateChatRecordTimeOut(-1);
        }

        @Override
        public void tv2Click() {
            updateChatRecordTimeOut(0.04);
            // updateChatRecordTimeOut(0.00347); // 五分钟过期
        }

        @Override
        public void tv3Click() {
            updateChatRecordTimeOut(1);
        }

        @Override
        public void tv4Click() {
            updateChatRecordTimeOut(7);
        }

        @Override
        public void tv5Click() {
            updateChatRecordTimeOut(30);
        }

        @Override
        public void tv6Click() {
            updateChatRecordTimeOut(90);
        }

        @Override
        public void tv7Click() {
            updateChatRecordTimeOut(365);
        }
    };
    private Uri mNewPhotoUri;
    private File mCurrentFile;
    private TextView tvMemberLimit;
    // 消息管理 && 全体禁言
    SwitchButton.OnCheckedChangeListener onCheckedChangeMessageListener = new SwitchButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(SwitchButton view, boolean isChecked) {
            switch (view.getId()) {
                case R.id.sb_top_chat:// 置顶聊天
                    if (isChecked) {
                        FriendDao.getInstance().updateTopFriend(mRoomJid, mRoom.getTimeSend());
                    } else {
                        FriendDao.getInstance().resetTopFriend(mRoomJid);
                    }
                    if (!isMucChatComing) {// 非聊天界面进入，需要刷新消息页面
                        MsgBroadcast.broadcastMsgUiUpdate(RoomInfoActivity.this);
                    }
                    break;
                case R.id.sb_no_disturb:// 消息免打扰
                    updateDisturbState(isChecked ? 1 : 0);
                    break;
                case R.id.sb_shield_chat:// 屏蔽群消息
                    if (isChecked) {
                        if (mRoom.getOfflineNoPushMsg() == 0) {
                            mSbDisturb.setChecked(true);
                        }
                    }
                    PreferenceUtils.putBoolean(mContext, Constants.SHIELD_GROUP_MSG + mRoomJid + mLoginUserId, isChecked);
                    mSbShield.setChecked(isChecked);
                    break;
                case R.id.sb_banned:// 全体禁言
                    if (isChecked) {
                        updateSingleAttribute("talkTime", String.valueOf(TimeUtils.sk_time_current_time() + 24 * 60 * 60 * 15));
                    } else {
                        updateSingleAttribute("talkTime", String.valueOf(0));
                    }
                    break;
            }
        }
    };
    private int add_minus_count = 2;
    private int role;
    // 跳转至邀请群成员界面需要的参数
    private String creator;  // 群主id
    private int isNeedVerify;// 是否开启进群验证
    // 展开与收起群成员列表
    private LinearLayout llOp;
    private ImageView mOpenMembers;
    // false表示折叠状态，
    private boolean flag;
    private List<MucRoomMember> mMembers;
    private List<MucRoomMember> mCurrentMembers = new ArrayList<>();
    private MucRoomMember mGroupOwner;// 群主
    private MucRoomMember myself;// 自己
    private Map<String, String> mRemarksMap = new HashMap<>();
    private View header;
    private View footer;
    private int mMemberSize;

    /**
     * 群成员分页
     * 保存当前群组该页的最后一个成员入群时间 MucRoomMember
     */
    public static void saveMucLastRoamingTime(String ownerId, String roomId, long time, boolean reset) {
        if (reset) {
            PreferenceUtils.putLong(MyApplication.getContext(), Constants.MUC_MEMBER_LAST_JOIN_TIME + ownerId + roomId, time);
        } else {
            long lastRoamingTime = PreferenceUtils.getLong(MyApplication.getContext(), Constants.MUC_MEMBER_LAST_JOIN_TIME + ownerId + roomId, 0);
            if (lastRoamingTime < time) {
                PreferenceUtils.putLong(MyApplication.getContext(), Constants.MUC_MEMBER_LAST_JOIN_TIME + ownerId + roomId, time);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_info);
        if (getIntent() != null) {
            mRoomJid = getIntent().getStringExtra(AppConstant.EXTRA_USER_ID);
            isMucChatComing = getIntent().getBooleanExtra(AppConstant.EXTRA_IS_GROUP_CHAT, false);
        }
        if (TextUtils.isEmpty(mRoomJid)) {
            LogUtils.log(getIntent());
            Reporter.post("传入的RoomJid为空，");
            Toast.makeText(this, R.string.tip_group_message_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mLoginUserId = coreManager.getSelf().getUserId();
        mRoom = FriendDao.getInstance().getFriend(mLoginUserId, mRoomJid);
        if (mRoom == null || TextUtils.isEmpty(mRoom.getRoomId())) {
            LogUtils.log(getIntent());
            LogUtils.log("mLoginUserId = " + mLoginUserId);
            LogUtils.log("mRoomJid = " + mRoomJid);
            // 没有toString方法，暂且转json，不能被混淆，
            LogUtils.log("mRoom = " + JSON.toJSONString(mRoom));
            Reporter.post("传入的RoomJid找不到Room，");
            Toast.makeText(this, R.string.tip_group_message_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        initActionBar();
        initView();
        registerRefreshReceiver();
        loadMembers();
        initEvent();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception e) {
                // onCreate异常时可能没走到绑定Receiver,
                // 无论如何都不应该在destroy时崩溃，
                // 重复上报，可以加个boolean判断避免，无所谓了，
                Reporter.post("解绑Receiver异常，", e);
            }
        }
        // 这个没注册时取消注册也不会崩溃，
        EventBus.getDefault().unregister(this);
    }

    private void initActionBar() {
        getSupportActionBar().hide();
        findViewById(R.id.iv_title_left).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        TextView mTitleTv = (TextView) findViewById(R.id.tv_title_center);
        mTitleTv.setText(InternationalizationHelper.getString("JXRoomMemberVC_RoomInfo"));
    }

    private void initView() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Friend> mFriendList = FriendDao.getInstance().getAllFriends(mLoginUserId);
                for (int i = 0; i < mFriendList.size(); i++) {
                    if (!TextUtils.isEmpty(mFriendList.get(i).getRemarkName())) {// 针对该好友进行了备注
                        mRemarksMap.put(mFriendList.get(i).getUserId(), mFriendList.get(i).getRemarkName());
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mAdapter != null) {
                            mAdapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        }).start();

        mGridView = findViewById(R.id.grid_view);
        // 很多控件都在footer里，
        header = getLayoutInflater().inflate(R.layout.activity_room_info_header, null);
        footer = getLayoutInflater().inflate(R.layout.activity_room_info_footer, null);
        mGridView.addHeaderView(header);
        mGridView.addFooterView(footer);
        llOp = (LinearLayout) footer.findViewById(R.id.ll_op);
        mOpenMembers = (ImageView) footer.findViewById(R.id.open_members);
        mRoomNameTv = (TextView) footer.findViewById(R.id.room_name_tv);
        mRoomDescTv = (TextView) footer.findViewById(R.id.room_desc_tv);
        mNoticeTv = (TextView) footer.findViewById(R.id.notice_tv);
        mNickNameTv = (TextView) footer.findViewById(R.id.nick_name_tv);
        room_qrcode = (RelativeLayout) footer.findViewById(R.id.room_qrcode);

        mSbTopChat = (SwitchButton) footer.findViewById(R.id.sb_top_chat);
        mSbDisturb = (SwitchButton) footer.findViewById(R.id.sb_no_disturb);
        mSbShield = (SwitchButton) footer.findViewById(R.id.sb_shield_chat);

        mSbAllShutUp = (SwitchButton) footer.findViewById(R.id.sb_banned);

        gongGaoTv = (TextView) footer.findViewById(R.id.notice_text);
        romNameTv = (TextView) footer.findViewById(R.id.room_name_text);
        romDesTv = (TextView) footer.findViewById(R.id.room_desc_text);
        myGroupName = (TextView) footer.findViewById(R.id.nick_name_text);
        shieldGroupMesTv = (TextView) footer.findViewById(R.id.shield_chat_text_title);
        jinyanTv = (TextView) footer.findViewById(R.id.banned_voice_text);
        gongGaoTv.setText(InternationalizationHelper.getString("JXRoomMemberVC_RoomAdv"));
        romNameTv.setText(InternationalizationHelper.getString("JX_RoomName"));
        /*romDesTv.setText(InternationalizationHelper.getString("JX_RoomExplain"));*/
        myGroupName.setText(InternationalizationHelper.getString("JXRoomMemberVC_NickName"));
        shieldGroupMesTv.setText(InternationalizationHelper.getString("JXRoomMemberVC_NotMessage"));
        jinyanTv.setText(InternationalizationHelper.getString("GAG"));
      /*  TextView qrCode = (TextView) footer.findViewById(R.id.qr_code_tv);
        qrCode.setText(InternationalizationHelper.getString("JXQR_QRImage"));*/
        TextView mGroupFile = (TextView) footer.findViewById(R.id.tv_file_name);
        mGroupFile.setText(InternationalizationHelper.getString("JXRoomMemberVC_ShareFile"));
       /* TextView isGroupReadTv = (TextView) footer.findViewById(R.id.iskaiqiqun);
        isGroupReadTv.setText(InternationalizationHelper.getString("JX_RoomShowRead"));*/

        mBtnQuitRoom = (Button) footer.findViewById(R.id.room_info_quit_btn);
        mBtnQuitRoom.setBackground(new ColorDrawable(MyApplication.getContext().getResources().getColor(R.color.redpacket_bg)));
        mBtnQuitRoom.setText(InternationalizationHelper.getString("JXRoomMemberVC_OutPutRoom"));

        // ExpandView And His Sons
        mExpandIv = (ImageView) footer.findViewById(R.id.room_info_iv);
        mExpandView = (ExpandView) footer.findViewById(R.id.expandView);
        mExpandView.setContentView(R.layout.layout_expand);
        mCreatorTv = (TextView) footer.findViewById(R.id.creator_tv);
        buileTimetv = (TextView) footer.findViewById(R.id.create_time_text);
        buileTimetv.setText(InternationalizationHelper.getString("JXRoomMemberVC_CreatTime"));
        mCreateTime = (TextView) footer.findViewById(R.id.create_timer);
        numberTopTv = (TextView) footer.findViewById(R.id.count_text);
        numberTopTv.setText(InternationalizationHelper.getString("MEMBER_CAP"));
        mCountTv = (TextView) footer.findViewById(R.id.count_tv);
        mCountTv2 = (TextView) header.findViewById(R.id.member_count_tv);
        mMsgSaveDays = (TextView) footer.findViewById(R.id.msg_save_days_tv);

        // 获取群组数据需要一定时间，我们先从朋友表内获取部分数据赋值，待服务器返回数据后在刷新ui
        mRoomNameTv.setText(mRoom.getNickName());
        mRoomDescTv.setText(mRoom.getDescription());

        mNickNameTv.setText(mRoom.getRoomMyNickName() != null
                ? mRoom.getRoomMyNickName() : coreManager.getSelf().getNickName());

        mSbDisturb.setChecked(mRoom.getOfflineNoPushMsg() == 1);// 消息免打扰
        mMsgSaveDays.setText(conversion(mRoom.getChatRecordTimeOut()));// 消息保存天数

        boolean isAllShutUp = PreferenceUtils.getBoolean(mContext, Constants.GROUP_ALL_SHUP_UP + mRoom.getUserId(), false);
        mSbAllShutUp.setChecked(isAllShutUp);

        tvMemberLimit = footer.findViewById(R.id.member_limit_tv);
    }

    /**
     * 所有角色都拥有的权限
     */
    private void initEvent() {
        footer.findViewById(R.id.room_info).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mExpandView.isExpand()) {
                    mExpandView.collapse();
                    mExpandIv.setBackgroundResource(R.drawable.open_member);
                } else {
                    mExpandView.expand();
                    mExpandIv.setBackgroundResource(R.drawable.close_member);
                }
            }
        });

        // 二维码
        room_qrcode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RoomInfoActivity.this, QRcodeActivity.class);
                intent.putExtra("isgroup", true);
                intent.putExtra("userid", mRoom.getRoomId());
                intent.putExtra("roomJid", mRoom.getUserId());
                startActivity(intent);
            }
        });

        // 公告
        footer.findViewById(R.id.notice_rl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mucRoom != null) {
                    List<String> mNoticeIdList = new ArrayList<>();
                    List<String> mNoticeUserIdList = new ArrayList<>();
                    List<String> mNoticeNickNameIdList = new ArrayList<>();
                    List<Long> mNoticeTimeList = new ArrayList<>();
                    List<String> mNoticeTextList = new ArrayList<>();
                    for (Notice notice : mucRoom.getNotices()) {
                        mNoticeIdList.add(notice.getId());
                        mNoticeUserIdList.add(notice.getUserId());
                        mNoticeNickNameIdList.add(notice.getNickname());
                        mNoticeTimeList.add(notice.getTime());
                        mNoticeTextList.add(notice.getText());
                    }
                    Intent intent = new Intent(RoomInfoActivity.this, NoticeListActivity.class);
                    intent.putExtra("mNoticeIdList", JSON.toJSONString(mNoticeIdList));
                    intent.putExtra("mNoticeUserIdList", JSON.toJSONString(mNoticeUserIdList));
                    intent.putExtra("mNoticeNickNameIdList", JSON.toJSONString(mNoticeNickNameIdList));
                    intent.putExtra("mNoticeTimeList", JSON.toJSONString(mNoticeTimeList));
                    intent.putExtra("mNoticeTextList", JSON.toJSONString(mNoticeTextList));
                    intent.putExtra("mRole", myself.getRole());
                    intent.putExtra("mRoomId", mRoom.getRoomId());

                    startActivityForResult(intent, RESULT_FOR_MODIFY_NOTICE);
                }
            }
        });

        // 修改群内昵称
        footer.findViewById(R.id.nick_name_rl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangeNickNameDialog(mNickNameTv.getText().toString().trim());
            }
        });
        // 修改群头像
        footer.findViewById(R.id.picture_rl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showChangePictureDialog();
            }
        });

        // 群共享文件
        footer.findViewById(R.id.file_rl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (myself != null && mucRoom != null) {
                    Intent intent = new Intent(RoomInfoActivity.this, MucFileListActivity.class);
                    intent.putExtra("roomId", mRoom.getRoomId());
                    intent.putExtra("role", myself.getRole());
                    intent.putExtra("allowUploadFile", mucRoom.getAllowUploadFile());
                    startActivity(intent);
                }
            }
        });

        // 查找聊天记录
        footer.findViewById(R.id.chat_history_search).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RoomInfoActivity.this, SearchChatHistoryActivity.class);
                intent.putExtra("isSearchSingle", false);
                intent.putExtra(AppConstant.EXTRA_USER_ID, mRoomJid);
                intent.putExtra("roomId", mucRoom.getId());

                startActivity(intent);
            }
        });

        mSbTopChat.setOnCheckedChangeListener(onCheckedChangeMessageListener);
        mSbDisturb.setOnCheckedChangeListener(onCheckedChangeMessageListener);
        mSbShield.setOnCheckedChangeListener(onCheckedChangeMessageListener);

        // 清空聊天记录
        footer.findViewById(R.id.chat_history_empty).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SelectionFrame selectionFrame = new SelectionFrame(mContext);
                selectionFrame.setSomething(null, getString(R.string.tip_confirm_clean_history), new SelectionFrame.OnSelectionFrameClickListener() {
                    @Override
                    public void cancelClick() {

                    }

                    @Override
                    public void confirmClick() {
                        // 清空聊天记录
                        FriendDao.getInstance().resetFriendMessage(mLoginUserId, mRoomJid);
                        ChatMessageDao.getInstance().deleteMessageTable(mLoginUserId, mRoomJid);
                        sendBroadcast(new Intent(Constants.CHAT_HISTORY_EMPTY));// 清空聊天界面
                        MsgBroadcast.broadcastMsgUiUpdate(RoomInfoActivity.this);
                        Toast.makeText(RoomInfoActivity.this, getString(R.string.delete_success), Toast.LENGTH_SHORT).show();
                    }
                });
                selectionFrame.show();
            }
        });

        footer.findViewById(R.id.report_rl).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ReportDialog mReportDialog = new ReportDialog(RoomInfoActivity.this, true, new ReportDialog.OnReportListItemClickListener() {
                    @Override
                    public void onReportItemClick(Report report) {
                        report(mRoom.getRoomId(), report);
                    }
                });
                mReportDialog.show();
            }
        });

        // 退出群组
        mBtnQuitRoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mucRoom == null) {
                    return;
                }

                String desc;
                String url;
                Map<String, String> params = new HashMap<>();
                params.put("access_token", coreManager.getSelfStatus().accessToken);
                params.put("roomId", mRoom.getRoomId());
                if (mucRoom.getUserId().equals(mLoginUserId)) {// 解散群组
                    desc = getString(R.string.tip_disband);
                    url = coreManager.getConfig().ROOM_DELETE;
                } else {// 退出群组
                    params.put("userId", mLoginUserId);
                    desc = getString(R.string.tip_exit);
                    url = coreManager.getConfig().ROOM_MEMBER_DELETE;
                }
                quitRoom(desc, url, params);
            }
        });

        mGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (add_minus_count == 1) {
                    // 现在添加了群组成员折叠功能,+ -号都是一直存在的，所以需要修改下逻辑，不过add_minus_count可用做于判断权限
                    if (position == mCurrentMembers.size() - 2) {
                        if (myself.disallowInvite()) {
                            tip(getString(R.string.tip_disallow_invite_role_place_holder, getString(myself.getRoleName())));
                        } else if (mucRoom.getAllowInviteFriend() == 1 || myself.getRole() == 1 || myself.getRole() == 2) {
                            List<String> existIds = new ArrayList<>();
                            for (int i = 0; i < mMembers.size() - 2; i++) {
                                existIds.add(mMembers.get(i).getUserId());
                            }
                            // 邀请
                            Intent intent = new Intent(RoomInfoActivity.this, AddContactsActivity.class);
                            intent.putExtra("roomId", mRoom.getRoomId());
                            intent.putExtra("roomJid", mRoomJid);
                            intent.putExtra("roomName", mRoomNameTv.getText().toString());
                            intent.putExtra("roomDes", mRoomDescTv.getText().toString());
                            intent.putExtra("exist_ids", JSON.toJSONString(existIds));
                            intent.putExtra("roomCreator", creator);
                            startActivityForResult(intent, RESULT_FOR_ADD_MEMBER);
                        } else {
                            tip(getString(R.string.tip_disable_invite));
                        }
                    } else if (position == mCurrentMembers.size() - 1) {
                        // 群主或管理员才有权限操作
                        Toast.makeText(RoomInfoActivity.this, InternationalizationHelper.getString("JXRoomMemberVC_NotAdminCannotDoThis"), Toast.LENGTH_SHORT).show();
                    } else {
                        boolean isAllowSecretlyChat = PreferenceUtils.getBoolean(mContext, Constants.IS_SEND_CARD + mRoom.getUserId(), true);
                        if (isAllowSecretlyChat) {
                            MucRoomMember member = mCurrentMembers.get(position);
                            if (member != null) {
                                startBasicInfo(member.getUserId());
                            }
                        } else {
                            tip(getString(R.string.tip_member_disable_privately_chat));
                        }
                    }
                } else if (add_minus_count == 2) {// 群主与管理员
                    if (position == mCurrentMembers.size() - 2) {
                        List<String> existIds = new ArrayList<>();
                        for (int i = 0; i < mMembers.size() - 2; i++) {
                            existIds.add(mMembers.get(i).getUserId());
                        }
                        // 邀请
                        Intent intent = new Intent(RoomInfoActivity.this, AddContactsActivity.class);
                        intent.putExtra("roomId", mRoom.getRoomId());
                        intent.putExtra("roomJid", mRoomJid);
                        intent.putExtra("roomName", mRoomNameTv.getText().toString());
                        intent.putExtra("roomDes", mRoomDescTv.getText().toString());
                        intent.putExtra("exist_ids", JSON.toJSONString(existIds));
                        intent.putExtra("roomCreator", creator);
                        startActivityForResult(intent, RESULT_FOR_ADD_MEMBER);
                    } else if (position == mCurrentMembers.size() - 1) {
                        Intent intent = new Intent(mContext, GroupMoreFeaturesActivity.class);
                        intent.putExtra("roomId", mucRoom.getId());
                        intent.putExtra("isDelete", true);
                        startActivity(intent);
                    } else {
                        MucRoomMember member = mCurrentMembers.get(position);
                        if (member != null) {
                            startBasicInfo(member.getUserId());
                        }
                    }
                }
            }
        });
    }

    private void startBasicInfo(String userId) {
        BasicInfoActivity.start(mContext, userId, BasicInfoActivity.FROM_ADD_TYPE_GROUP);
    }

    private void loadMembers() {
        HashMap<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", mRoom.getRoomId());
        params.put("pageSize", Constants.MUC_MEMBER_PAGE_SIZE);

        HttpUtils.get().url(coreManager.getConfig().ROOM_GET)
                .params(params)
                .build()
                .execute(new BaseCallback<MucRoom>(MucRoom.class) {

                             @Override
                             public void onResponse(ObjectResult<MucRoom> result) {
                                 if (result.getResultCode() == 1 && result.getData() != null) {
                                     mucRoom = result.getData();
                                     tvMemberLimit.setText(String.valueOf(mucRoom.getMaxUserSize()));

                                     MyApplication.getInstance().saveGroupPartStatus(mucRoom.getJid(), mucRoom.getShowRead(), mucRoom.getAllowSendCard(),
                                             mucRoom.getAllowConference(), mucRoom.getAllowSpeakCourse(), mucRoom.getTalkTime());
                                     FriendDao.getInstance().updateRoomCreateUserId(mLoginUserId, mRoom.getUserId(), mucRoom.getUserId());
                                     PreferenceUtils.putBoolean(MyApplication.getContext(),
                                             Constants.IS_NEED_OWNER_ALLOW_NORMAL_INVITE_FRIEND + mucRoom.getJid(), mucRoom.getIsNeedVerify() == 1);
                                     PreferenceUtils.putBoolean(MyApplication.getContext(),
                                             Constants.IS_ALLOW_NORMAL_SEND_UPLOAD + mucRoom.getJid(), mucRoom.getAllowUploadFile() == 1);

                                     AsyncUtils.doAsync(this, (AsyncUtils.Function<AsyncUtils.AsyncContext<BaseCallback<MucRoom>>>) baseCallbackAsyncContext -> {
                                         for (int i = 0; i < mucRoom.getMembers().size(); i++) {// 在异步任务内存储
                                             RoomMember roomMember = new RoomMember();
                                             roomMember.setRoomId(mucRoom.getId());
                                             roomMember.setUserId(mucRoom.getMembers().get(i).getUserId());
                                             roomMember.setUserName(mucRoom.getMembers().get(i).getNickName());
                                             if (TextUtils.isEmpty(mucRoom.getMembers().get(i).getRemarkName())) {
                                                 roomMember.setCardName(mucRoom.getMembers().get(i).getNickName());
                                             } else {
                                                 roomMember.setCardName(mucRoom.getMembers().get(i).getRemarkName());
                                             }
                                             roomMember.setRole(mucRoom.getMembers().get(i).getRole());
                                             roomMember.setCreateTime(mucRoom.getMembers().get(i).getCreateTime());
                                             RoomMemberDao.getInstance().saveSingleRoomMember(mucRoom.getId(), roomMember);
                                         }
                                     });

                                     saveMucLastRoamingTime(mLoginUserId, mucRoom.getId(), mucRoom.getMembers().get(mucRoom.getMembers().size() - 1).getCreateTime(), false);

                                     // 更新消息界面
                                     MsgBroadcast.broadcastMsgUiUpdate(RoomInfoActivity.this);
                                     // 更新群聊界面
                                     MucgroupUpdateUtil.broadcastUpdateUi(RoomInfoActivity.this);
                                     // 更新ui
                                     updateUI(result.getData());
                                 } else {
                                     ToastUtil.showErrorData(RoomInfoActivity.this);
                                 }
                             }

                             @Override
                             public void onError(Call call, Exception e) {
                                 ToastUtil.showErrorNet(RoomInfoActivity.this);
                             }
                         }
                );
    }

    private void updateUI(final MucRoom mucRoom) {
        mMemberSize = mucRoom.getUserSize();
        mMembers = mucRoom.getMembers();

        creator = mucRoom.getUserId();
        isNeedVerify = mucRoom.getIsNeedVerify();

        if (mMembers != null) {
            for (int i = 0; i < mMembers.size(); i++) {
                String userId = mMembers.get(i).getUserId();
                if (mucRoom.getUserId().equals(userId)) {
                    mGroupOwner = mMembers.get(i);
                }

                if (mLoginUserId.equals(userId)) {
                    myself = mMembers.get(i);
                }
            }

            // 将群主移动到第一个的位置
            if (mGroupOwner != null) {
                mMembers.remove(mGroupOwner);
                mMembers.add(0, mGroupOwner);
            }
        }

        if (myself == null) {
            ToastUtil.showToast(mContext, R.string.tip_kick_room);
            finish();
            return;
        }

        mAdapter = new GridViewAdapter();
        mGridView.setAdapter(mAdapter);

        mRoomNameTv.setText(mucRoom.getName());
        mRoomDescTv.setText(mucRoom.getDesc());

        mCreatorTv.setText(mucRoom.getNickName());
        mCreateTime.setText(TimeUtils.s_long_2_str(mucRoom.getCreateTime() * 1000));
        mCountTv.setText(mucRoom.getUserSize() + "/" + mucRoom.getMaxUserSize());
        mCountTv2.setText(getString(R.string.total_count_place_holder, mucRoom.getUserSize()));

        List<Notice> notices = mucRoom.getNotices();
        if (notices != null && !notices.isEmpty()) {
            String text = getLastNoticeText(notices);
            mNoticeTv.setText(text);
        } else {
            mNoticeTv.setText(InternationalizationHelper.getString("JX_NotAch"));
        }
        String mGroupName = coreManager.getSelf().getNickName();
        if (mRoom != null) {
            mGroupName = mRoom.getRoomMyNickName() != null ?
                    mRoom.getRoomMyNickName() : mGroupName;
        }
        mNickNameTv.setText(mGroupName);

        // 更新消息免打扰状态
        mRoom.setOfflineNoPushMsg(myself.getOfflineNoPushMsg());
        FriendDao.getInstance().updateOfflineNoPushMsgStatus(mRoom.getUserId(), myself.getOfflineNoPushMsg());

        // 更新消息管理状态
        updateMessageStatus(myself.getOfflineNoPushMsg());

        // 更新消息保存天数
        mMsgSaveDays.setText(conversion(mucRoom.getChatRecordTimeOut()));
        FriendDao.getInstance().updateChatRecordTimeOut(mRoom.getUserId(), mucRoom.getChatRecordTimeOut());

        // 根据我在该群职位显示UI界面
        role = myself.getRole();
        /*for (int i = 0; i < mMembers.size(); i++) {
            if (mMembers.get(i).getUserId().equals(mLoginUserId)) {
                role = mMembers.get(i).getRole();
            }
        }*/

        if (role == 1) {// 群创建者，开放所有权限
            mBtnQuitRoom.setText(InternationalizationHelper.getString("DISSOLUTION_GROUP"));
            footer.findViewById(R.id.room_name_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showChangeRoomNameDialog(mRoomNameTv.getText().toString().trim());
                }
            });

            footer.findViewById(R.id.room_desc_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showChangeRoomDesDialog(mRoomDescTv.getText().toString().trim());
                }
            });

            footer.findViewById(R.id.msg_save_days_rl).setVisibility(View.VISIBLE);
            footer.findViewById(R.id.msg_save_days_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    MsgSaveDaysDialog msgSaveDaysDialog = new MsgSaveDaysDialog(RoomInfoActivity.this, onMsgSaveDaysDialogClickListener);
                    msgSaveDaysDialog.show();
                }
            });

            footer.findViewById(R.id.banned_voice_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(mContext, GroupMoreFeaturesActivity.class);
                    intent.putExtra("roomId", mucRoom.getId());
                    intent.putExtra("isBanned", true);
                    startActivity(intent);
                }
            });

            footer.findViewById(R.id.rl_manager).setVisibility(View.VISIBLE);
            footer.findViewById(R.id.rl_manager).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int status_lists[] = {mucRoom.getShowRead(), mucRoom.getIsLook(), mucRoom.getIsNeedVerify(),
                            mucRoom.getShowMember(), mucRoom.getAllowSendCard(),
                            mucRoom.getAllowInviteFriend(), mucRoom.getAllowUploadFile(),
                            mucRoom.getAllowConference(), mucRoom.getAllowSpeakCourse(),
                            mucRoom.getIsAttritionNotice()};
                    Intent intent = new Intent(mContext, GroupManager.class);
                    intent.putExtra("roomId", mucRoom.getId());
                    intent.putExtra("roomJid", mucRoom.getJid());
                    intent.putExtra("GROUP_STATUS_LIST", status_lists);
                    startActivity(intent);
                }
            });

            mSbAllShutUp.setOnCheckedChangeListener(onCheckedChangeMessageListener);

            enableGroupMore(mucRoom);

            updateMemberLimit(true);
        } else if (role == 2) {// 管理员，开放部分权限
            mBtnQuitRoom.setText(InternationalizationHelper.getString("JXRoomMemberVC_OutPutRoom"));
            footer.findViewById(R.id.room_name_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showChangeRoomNameDialog(mRoomNameTv.getText().toString().trim());
                }
            });

            footer.findViewById(R.id.room_desc_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showChangeRoomDesDialog(mRoomDescTv.getText().toString().trim());
                }
            });

            footer.findViewById(R.id.banned_voice_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(mContext, GroupMoreFeaturesActivity.class);
                    intent.putExtra("roomId", mucRoom.getId());
                    intent.putExtra("isBanned", true);
                    startActivity(intent);
                }
            });

            mSbAllShutUp.setOnCheckedChangeListener(onCheckedChangeMessageListener);

            enableGroupMore(mucRoom);

            updateMemberLimit(true);
        } else {
            add_minus_count = 1;
            mBtnQuitRoom.setText(InternationalizationHelper.getString("JXRoomMemberVC_OutPutRoom"));
            footer.findViewById(R.id.room_name_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tip(getString(R.string.tip_cannot_change_name));
                }
            });

            footer.findViewById(R.id.room_desc_rl).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tip(getString(R.string.tip_cannot_change_description));
                }
            });

            // 隐藏 禁言 与 全体禁言
            footer.findViewById(R.id.banned_voice_rl).setVisibility(View.GONE);
            footer.findViewById(R.id.banned_all_voice_rl).setVisibility(View.GONE);

            footer.findViewById(R.id.msg_save_days_rl).setVisibility(View.GONE);
            footer.findViewById(R.id.rl_manager).setVisibility(View.GONE);

            boolean isAllowSecretlyChat = PreferenceUtils.getBoolean(mContext, Constants.IS_SEND_CARD + mRoom.getUserId(), true);
            if (isAllowSecretlyChat) {
                enableGroupMore(mucRoom);
            }
            updateMemberLimit(false);
        }

        // 现在添加群组成员折叠功能，让+ -号一直存在吧
        mMembers.add(null);// 一个+号
        mMembers.add(null);// 一个-号

        mCurrentMembers.clear();
        if (mucRoom.getShowMember() == 0 && role != 1 && role != 2) {// 群主已关闭 显示群成员列表功能 (群主与管理员可见) 普通成员只显示自己与+ -
            header.findViewById(R.id.ll_all_member).setVisibility(View.GONE);
            llOp.setVisibility(View.GONE);
            mCurrentMembers.add(mGroupOwner);
            mCurrentMembers.add(myself);
            mCurrentMembers.add(null);// +
            mCurrentMembers.add(null);// _
        } else {// 正常加载
            header.findViewById(R.id.ll_all_member).setVisibility(View.VISIBLE);
            // 减去+-两个按钮，
            if (mMembers.size() - 2 > getDefaultCount()) {
                // 可以折叠
                llOp.setVisibility(View.VISIBLE);
                // 折叠显示，
                // 确保群成员变化后刷新群成员列表默认折叠时flag没有错，
                flag = false;
                mOpenMembers.setImageResource(R.drawable.open_member);
                minimalMembers();
            } else {
                // 不可折叠，全部显示
                llOp.setVisibility(View.GONE);
                mCurrentMembers.addAll(mMembers);
            }

            llOp.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    op();
                }
            });
        }
    }

    /**
     * @param isGroupManager 群主或群管理员，
     */
    private void updateMemberLimit(boolean isGroupManager) {
        View rlMemberLimit = footer.findViewById(R.id.member_limit_rl);
        if (isGroupManager && coreManager.getSelf().isSuperManager()) {
            rlMemberLimit.setVisibility(View.VISIBLE);
            rlMemberLimit.setOnClickListener(v -> {
                DialogHelper.input(this, "设置群人数上限", "群人数上限", new VerifyDialog.VerifyClickListener() {
                    @Override
                    public void cancel() {

                    }

                    @Override
                    public void send(String str) {
                        if (TextUtils.isDigitsOnly(str)) {
                            updateSingleAttribute("maxUserSize", str);
                        } else {
                            Reporter.unreachable();
                            ToastUtil.showToast(RoomInfoActivity.this, "数字格式不正确");
                        }
                    }
                });
            });
        } else {
            rlMemberLimit.setVisibility(View.GONE);
        }
    }

    /**
     * 允许点击群人数进入群成员更多操作的页面，
     */
    private void enableGroupMore(MucRoom mucRoom) {
        header.findViewById(R.id.ll_all_member).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(mContext, GroupMoreFeaturesActivity.class);
                intent.putExtra("roomId", mucRoom.getId());
                intent.putExtra("isLoadByService", true);
                startActivity(intent);
            }
        });
    }

    private void minimalMembers() {
        int count = getDefaultCount();
        for (int i = 0; i < count; i++) {
            mCurrentMembers.add(mMembers.get(i));
        }
        mCurrentMembers.add(null);
        mCurrentMembers.add(null);
    }

    private int getDefaultCount() {
        return mGridView.getNumColumns() * 3 - 2;
    }

    /**
     * 调用该方法的情况，
     * mMembers.size > getDefaultCount() + 2
     * mMembers.size包括了+-两个按钮，
     */
    public void op() {
        Log.e("RoomInfoActivity", System.currentTimeMillis() + "start");
        flag = !flag;
        mCurrentMembers.clear();
        if (flag) {
            // 展开
            mCurrentMembers.addAll(mMembers);
            mAdapter.notifyDataSetChanged();
            mOpenMembers.setImageResource(R.drawable.close_member);
        } else {
            // 收起
            minimalMembers();
            mAdapter.notifyDataSetChanged();
            scrollToTop();
            mOpenMembers.setImageResource(R.drawable.open_member);
        }
        Log.e("RoomInfoActivity", System.currentTimeMillis() + "end");
    }

    public void tip(String tip) {

        ToastUtil.showToast(RoomInfoActivity.this, tip);
    }

    private String getLastNoticeText(List<Notice> notices) {
        Notice notice = new Notice();
        notice.setTime(0);
        for (Notice no : notices) {
            if (no.getTime() > notice.getTime())
                notice = no;
        }
        return notice.getText();
    }

    // 修改群组名称
    private void showChangeRoomNameDialog(final String roomName) {
        DialogHelper.showLimitSingleInputDialog(this, InternationalizationHelper.getString("JXRoomMemberVC_UpdateRoomName"), roomName,
                2, 2, 20, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String text = ((EditText) v).getText().toString().trim();
                        if (TextUtils.isEmpty(text) || text.equals(roomName)) {
                            return;
                        }
                        int length = 0;
                        for (int i = 0; i < text.length(); i++) {
                            String substring = text.substring(i, i + 1);
                            boolean flag = CharUtils.isChinese(substring);
                            if (flag) {
                                // 中文占两个字符
                                length += 2;
                            } else {
                                length += 1;
                            }
                        }
                        if (length > 20) {
                            ToastUtil.showToast(mContext, getString(R.string.tip_name_too_long));
                            return;
                        }
                        updateRoom(text, null);
                    }
                });
    }

    // 修改群组描述
    private void showChangeRoomDesDialog(final String roomDes) {
        DialogHelper.showLimitSingleInputDialog(this, InternationalizationHelper.getString("JXRoomMemberVC_UpdateExplain"), roomDes,
                7, 2, 100, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String text = ((EditText) v).getText().toString().trim();
                        if (TextUtils.isEmpty(text) || text.equals(roomDes)) {
                            return;
                        }
                        int length = 0;
                        for (int i = 0; i < text.length(); i++) {
                            String substring = text.substring(i, i + 1);
                            boolean flag = CharUtils.isChinese(substring);
                            if (flag) {
                                length += 2;
                            } else {
                                length += 1;
                            }
                        }
                        if (length > 100) {
                            ToastUtil.showToast(mContext, getString(R.string.tip_description_too_long));
                            return;
                        }
                        updateRoom(null, text);
                    }
                });
    }

    // 修改群头像
    private void showChangePictureDialog() {
        String[] items = new String[]{InternationalizationHelper.getString("PHOTOGRAPH"), InternationalizationHelper.getString("ALBUM")};
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(InternationalizationHelper.getString("SELECT_AVATARS"))
                .setSingleChoiceItems(items, 0,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    takePhoto();
                                } else {
                                    selectPhoto();
                                }
                                dialog.dismiss();
                            }
                        });
        builder.show();
    }

    private void takePhoto() {
        mNewPhotoUri = CameraUtil.getOutputMediaFileUri(this, CameraUtil.MEDIA_TYPE_IMAGE);
        CameraUtil.captureImage(this, mNewPhotoUri, REQUEST_CODE_CAPTURE_CROP_PHOTO);
    }

    private void selectPhoto() {
        CameraUtil.pickImageSimple(this, REQUEST_CODE_PICK_CROP_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_FOR_ADD_MEMBER && resultCode == RESULT_OK) {// 添加成员返回
            loadMembers();
        } else if (requestCode == RESULT_FOR_MODIFY_NOTICE && resultCode == RESULT_OK) {// 修改公告返回
            if (data != null) {
                boolean isNeedUpdate = data.getBooleanExtra("isNeedUpdate", false);
                if (isNeedUpdate) {
                    loadMembers();
                }
            }
        } else if (requestCode == REQUEST_CODE_CAPTURE_CROP_PHOTO) {// 拍照返回再去裁减
            if (resultCode == Activity.RESULT_OK) {
                if (mNewPhotoUri != null) {
                    Uri o = mNewPhotoUri;
                    mNewPhotoUri = CameraUtil.getOutputMediaFileUri(this, CameraUtil.MEDIA_TYPE_IMAGE);
                    mCurrentFile = new File(mNewPhotoUri.getPath());
                    CameraUtil.cropImage(this, o, mNewPhotoUri, REQUEST_CODE_CROP_PHOTO, 1, 1, 300, 300);
                } else {
                    ToastUtil.showToast(this, R.string.c_photo_album_failed);
                }
            }
        } else if (requestCode == REQUEST_CODE_PICK_CROP_PHOTO) {// 选择一张图片,然后立即调用裁减
            Log.e("zx", "onActivityResult: 选择一张图片");
            if (resultCode == Activity.RESULT_OK) {
                Log.e("zx", "onActivityResult: RESULT_OK 选择一张图片");

                if (data != null && data.getData() != null) {
                    Uri o = data.getData();
                    mNewPhotoUri = CameraUtil.getOutputMediaFileUri(this, CameraUtil.MEDIA_TYPE_IMAGE);
                    mCurrentFile = new File(mNewPhotoUri.getPath());
                    CameraUtil.cropImage(this, o, mNewPhotoUri, REQUEST_CODE_CROP_PHOTO, 1, 1, 300, 300);
                } else {
                    ToastUtil.showToast(this, R.string.c_photo_album_failed);
                }
            }
        } else if (requestCode == REQUEST_CODE_CROP_PHOTO) {
            if (resultCode == Activity.RESULT_OK) {
                if (mNewPhotoUri != null) {
                    mCurrentFile = new File(mNewPhotoUri.getPath());
                    // 上传头像
                    uploadAvatar(mCurrentFile);
                } else {
                    ToastUtil.showToast(this, R.string.c_crop_failed);
                }
            }
        }
    }

    private void uploadAvatar(File file) {
        if (!file.exists()) {
            // 文件不存在
            return;
        }
        // 显示正在上传的ProgressDialog
        DialogHelper.showDefaulteMessageProgressDialog(this);
        RequestParams params = new RequestParams();
        params.put("jid", mRoomJid);
        try {
            params.put("file", file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        AsyncHttpClient client = new AsyncHttpClient();
        client.post(coreManager.getConfig().ROOM_UPDATE_PICTURE, params, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int arg0, Header[] arg1, byte[] arg2) {
                DialogHelper.dismissProgressDialog();
                boolean success = false;
                if (arg0 == 200) {
                    Result result = null;
                    try {
                        result = JSON.parseObject(new String(arg2), Result.class);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (result != null && result.getResultCode() == Result.CODE_SUCCESS) {
                        success = true;
                    }
                }

                if (success) {
                    ToastUtil.showToast(mContext, R.string.upload_avatar_success);
                    AvatarHelper.getInstance().updateAvatar(mRoomJid);// 更新时间
                } else {
                    ToastUtil.showToast(mContext, R.string.upload_avatar_failed);
                }
            }

            @Override
            public void onFailure(int arg0, Header[] arg1, byte[] arg2, Throwable arg3) {
                DialogHelper.dismissProgressDialog();
                ToastUtil.showToast(mContext, R.string.upload_avatar_failed);
            }
        });
    }

    // 修改昵称
    private void showChangeNickNameDialog(final String nickName) {
        DialogHelper.showLimitSingleInputDialog(this, InternationalizationHelper.getString("JXRoomMemberVC_UpdateNickName"), nickName, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String text = ((EditText) v).getText().toString().trim();
                if (TextUtils.isEmpty(text) || text.equals(nickName)) {
                    return;
                }
                updateNickName(text);
            }
        });
    }

    // 更新消息状态 置顶 、免打扰、屏蔽
    private void updateMessageStatus(int disturb) {
        mSbTopChat.setChecked(mRoom.getTopTime() != 0);
        mSbDisturb.setChecked(disturb == 1);
        boolean mShieldStatus = PreferenceUtils.getBoolean(mContext, Constants.SHIELD_GROUP_MSG + mRoomJid + mLoginUserId, false);
        mSbShield.setChecked(mShieldStatus);
    }

    private String conversion(double outTime) {
        String outTimeStr;
        if (outTime == -1 || outTime == 0) {
            outTimeStr = getString(R.string.permanent);
        } else if (outTime == 0.04) {
            outTimeStr = getString(R.string.one_hour);
        } else if (outTime == 1) {
            outTimeStr = getString(R.string.one_day);
        } else if (outTime == 7) {
            outTimeStr = getString(R.string.one_week);
        } else if (outTime == 30) {
            outTimeStr = getString(R.string.one_month);
        } else if (outTime == 90) {
            outTimeStr = getString(R.string.one_season);
        } else {
            outTimeStr = getString(R.string.one_year);
        }
        return outTimeStr;
    }

    /**
     * ScrollView移动到最顶端
     */
    private void scrollToTop() {
        mGridView.post(() -> {
            mGridView.smoothScrollToPosition(0);
        });
    }

    @Subscribe(threadMode = ThreadMode.MainThread)
    public void helloEventBus(EventGroupStatus eventGroupStatus) {
        if (eventGroupStatus.getWhichStatus() == 0) {
            mucRoom.setShowRead(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 1) {
            mucRoom.setIsLook(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 2) {
            mucRoom.setIsNeedVerify(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 3) {
            mucRoom.setShowMember(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 4) {
            mucRoom.setAllowSendCard(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 5) {
            mucRoom.setAllowInviteFriend(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 6) {
            mucRoom.setAllowUploadFile(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 7) {
            mucRoom.setAllowConference(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 8) {
            mucRoom.setAllowSpeakCourse(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 9) {
            mucRoom.setIsAttritionNotice(eventGroupStatus.getGroupManagerStatus());
        } else if (eventGroupStatus.getWhichStatus() == 10000) {// 设置/取消 管理员, 隐身人，监控人，
            loadMembers();
        } else if (eventGroupStatus.getWhichStatus() == 10001) {// 删除群成员
            mMemberSize = mMemberSize - 1;
            mCountTv.setText(mMemberSize + "/" + mucRoom.getMaxUserSize());
            mCountTv2.setText(getString(R.string.total_count_place_holder, mMemberSize));
            for (int i = 0; i < mMembers.size(); i++) {
                if (mMembers.get(i).getUserId().equals(String.valueOf(eventGroupStatus.getGroupManagerStatus()))) {
                    mCurrentMembers.remove(mMembers.get(i));
                    mMembers.remove(mMembers.get(i));
                    mAdapter.notifyDataSetInvalidated();
                }
            }
        } else if (eventGroupStatus.getWhichStatus() == 10002) {// 转让群
            loadMembers();
        } else if (eventGroupStatus.getWhichStatus() == 10003) {// 备注
            loadMembers();
            // 需通知群聊页面刷新
            MsgBroadcast.broadcastMsgRoomUpdate(mContext);
        }
    }

    private void registerRefreshReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(com.sk.weichat.broadcast.OtherBroadcast.REFRESH_MANAGER);
        intentFilter.addAction(ACTION_MSG_UPDATE_ROOM_INVITE);
        registerReceiver(receiver, intentFilter);
    }


    /**
     * Todo Http Get
     * <p>
     * 修改群名称、描述
     */
    private void updateRoom(final String roomName, final String roomDes) {
        Map<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", mRoom.getRoomId());
        if (!TextUtils.isEmpty(roomName)) {
            params.put("roomName", roomName);
        }

        if (!TextUtils.isEmpty(roomDes)) {
            params.put("desc", roomDes);
        }
        DialogHelper.showDefaulteMessageProgressDialog(this);

        HttpUtils.get().url(coreManager.getConfig().ROOM_UPDATE)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        if (result.getResultCode() == 1) {
                            Toast.makeText(RoomInfoActivity.this, getString(R.string.update_success), Toast.LENGTH_SHORT).show();
                            if (!TextUtils.isEmpty(roomName)) {
                                mRoomNameTv.setText(roomName);
                                mRoom.setNickName(roomName);
                                FriendDao.getInstance().updateNickName(mLoginUserId, mRoom.getUserId(), roomName);
                            }

                            if (!TextUtils.isEmpty(roomDes)) {
                                mRoomDescTv.setText(roomDes);
                                mRoom.setDescription(roomDes);
                            }
                        } else {
                            Toast.makeText(RoomInfoActivity.this, result.getResultMsg(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                        ToastUtil.showErrorNet(mContext);
                    }
                });
    }

    /**
     * 更改群内昵称
     */
    private void updateNickName(final String nickName) {
        Map<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", mRoom.getRoomId());
        params.put("userId", mLoginUserId);
        params.put("nickname", nickName);
        DialogHelper.showDefaulteMessageProgressDialog(this);

        HttpUtils.get().url(coreManager.getConfig().ROOM_MEMBER_UPDATE)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        ToastUtil.showToast(mContext, R.string.update_success);
                        mNickNameTv.setText(nickName);
                        String loginUserId = coreManager.getSelf().getUserId();
                        FriendDao.getInstance().updateRoomName(loginUserId, mRoom.getUserId(), nickName);
                        ChatMessageDao.getInstance().updateNickName(loginUserId, mRoom.getUserId(), loginUserId, nickName);
                        mRoom.setRoomMyNickName(nickName);
                        FriendDao.getInstance().updateRoomMyNickName(mRoom.getUserId(), nickName);
                        ListenerManager.getInstance().notifyNickNameChanged(mRoom.getUserId(), loginUserId, nickName);
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                        ToastUtil.showErrorNet(mContext);
                    }
                });
    }

    /**
     * 消息免打扰
     */
    private void updateDisturbState(final int disturb) {
        Map<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", mRoom.getRoomId());
        params.put("userId", mLoginUserId);
        params.put("offlineNoPushMsg", String.valueOf(disturb));
        DialogHelper.showDefaulteMessageProgressDialog(this);

        HttpUtils.get().url(coreManager.getConfig().ROOM_DISTURB)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        if (result.getResultCode() == 1) {
                            mRoom.setOfflineNoPushMsg(disturb);
                            FriendDao.getInstance().updateOfflineNoPushMsgStatus(mRoom.getUserId(), disturb);
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                        ToastUtil.showErrorNet(mContext);
                    }
                });
    }

    /**
     * 更新群组内的某个属性
     */
    private void updateSingleAttribute(final String attributeKey, final String attributeValue) {
        Map<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", mRoom.getRoomId());
        params.put(attributeKey, attributeValue);
        DialogHelper.showDefaulteMessageProgressDialog(this);

        HttpUtils.get().url(coreManager.getConfig().ROOM_UPDATE)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        if (result.getResultCode() == 1) {
                            Toast.makeText(mContext, R.string.modify_succ, Toast.LENGTH_SHORT).show();
                            if (attributeKey.equals("talkTime")) {// 全体禁言
                            }
                            switch (attributeKey) {
                                case "talkTime":
                                    if (Long.parseLong(attributeValue) > 0) {// 开启全体禁言
                                        PreferenceUtils.putBoolean(mContext, Constants.GROUP_ALL_SHUP_UP + mRoom.getUserId(), true);
                                    } else {// 取消全体禁言
                                        PreferenceUtils.putBoolean(mContext, Constants.GROUP_ALL_SHUP_UP + mRoom.getUserId(), false);
                                    }
                                    break;
                                case "maxUserSize":
                                    mucRoom.setMaxUserSize(Integer.valueOf(attributeValue));
                                    tvMemberLimit.setText(attributeValue);
                                    break;
                            }
                        } else {
                            Toast.makeText(mContext, R.string.modify_fail, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                    }
                });
    }

    /**
     * 更新消息保存天数
     */
    private void updateChatRecordTimeOut(final double outTime) {
        Map<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", mRoom.getRoomId());
        params.put("chatRecordTimeOut", String.valueOf(outTime));

        HttpUtils.get().url(coreManager.getConfig().ROOM_UPDATE)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        if (result.getResultCode() == 1) {
                            Toast.makeText(RoomInfoActivity.this, getString(R.string.update_success), Toast.LENGTH_SHORT).show();
                            mMsgSaveDays.setText(conversion(outTime));
                            FriendDao.getInstance().updateChatRecordTimeOut(mRoom.getUserId(), outTime);

                            Intent intent = new Intent();
                            intent.setAction(Constants.CHAT_TIME_OUT_ACTION);
                            intent.putExtra("friend_id", mRoom.getUserId());
                            intent.putExtra("time_out", outTime);
                            mContext.sendBroadcast(intent);
                        } else {
                            Toast.makeText(RoomInfoActivity.this, result.getResultMsg(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                        ToastUtil.showErrorNet(mContext);
                    }
                });
    }

    /*
    举报
     */
    private void report(String roomId, Report report) {
        Map<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("roomId", roomId);
        params.put("reason", String.valueOf(report.getReportId()));
        DialogHelper.showDefaulteMessageProgressDialog(this);

        HttpUtils.get().url(coreManager.getConfig().USER_REPORT)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        if (result.getResultCode() == 1) {
                            ToastUtil.showToast(RoomInfoActivity.this, "举报成功");
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                    }
                });
    }

    /**
     * 退出群组
     */
    private void quitRoom(String desc, final String url, final Map<String, String> params) {
        SelectionFrame selectionFrame = new SelectionFrame(mContext);
        selectionFrame.setSomething(null, desc, new SelectionFrame.OnSelectionFrameClickListener() {
            @Override
            public void cancelClick() {

            }

            @Override
            public void confirmClick() {
                DialogHelper.showDefaulteMessageProgressDialog(RoomInfoActivity.this);
                HttpUtils.get().url(url)
                        .params(params)
                        .build()
                        .execute(new BaseCallback<Void>(Void.class) {

                            @Override
                            public void onResponse(ObjectResult<Void> result) {
                                DialogHelper.dismissProgressDialog();
                                if (result.getResultCode() == 1) {
                                    deleteFriend();
                                    if (isMucChatComing) {// 如果从聊天界面进入，退出 / 解散 群组需要销毁聊天界面
                                        Intent intent = new Intent(RoomInfoActivity.this, MainActivity.class);
                                        startActivity(intent);
                                    }
                                    finish();
                                } else {
                                    Toast.makeText(RoomInfoActivity.this, result.getResultMsg() + "", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onError(Call call, Exception e) {
                                DialogHelper.dismissProgressDialog();
                                ToastUtil.showErrorNet(RoomInfoActivity.this);
                            }
                        });
            }
        });
        selectionFrame.show();
    }

    private void deleteFriend() {
        // 删除这个房间
        FriendDao.getInstance().deleteFriend(mLoginUserId, mRoom.getUserId());
        // 消息表中删除
        ChatMessageDao.getInstance().deleteMessageTable(mLoginUserId, mRoom.getUserId());
        RoomMemberDao.getInstance().deleteRoomMemberTable(mRoom.getRoomId());
        // 更新消息界面
        MsgBroadcast.broadcastMsgNumReset(this);
        MsgBroadcast.broadcastMsgUiUpdate(this);
        // 更新群聊界面
        MucgroupUpdateUtil.broadcastUpdateUi(this);
        coreManager.exitMucChat(mRoom.getUserId());
    }

    public class RefreshBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(com.sk.weichat.broadcast.OtherBroadcast.REFRESH_MANAGER)) {
                String roomId = intent.getStringExtra("roomId");
                String toUserId = intent.getStringExtra("toUserId");
                boolean isSet = intent.getBooleanExtra("isSet", false);
                if (roomId.equals(mRoomJid) && toUserId.equals(mLoginUserId)) {
                    TipDialog tipDialog = new TipDialog(RoomInfoActivity.this);
                    tipDialog.setmConfirmOnClickListener(isSet ? getString(R.string.tip_became_manager) : getString(R.string.tip_be_cancel_manager)
                            , new TipDialog.ConfirmOnClickListener() {
                                @Override
                                public void confirm() {
                                    finish();
                                }
                            });
                    tipDialog.show();
                }
            } else if (action.equals(ACTION_MSG_UPDATE_ROOM_INVITE)) {
                if (mucRoom != null) {
                    int enabled = intent.getIntExtra(EXTRA_ENABLED, -1);
                    if (enabled != -1) {
                        mucRoom.setAllowInviteFriend(enabled);
                    }
                }
            }
        }
    }

    class GridViewAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mCurrentMembers.size();
        }

        @Override
        public Object getItem(int position) {
            return mCurrentMembers.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(R.layout.item_room_info_view, parent, false);
                GridViewHolder vh = new GridViewHolder(convertView);
                convertView.setTag(vh);
            }
            GridViewHolder vh = (GridViewHolder) convertView.getTag();
            ImageView imageView = vh.imageView;
            ImageView ivFrame = vh.ivFrame;
            TextView memberName = vh.memberName;
            int GAT5;
            if (add_minus_count == 1) {
                GAT5 = add_minus_count + 2;
            } else {
                GAT5 = add_minus_count + 1;
            }
            if (position > mCurrentMembers.size() - GAT5) {// + -
                memberName.setText("");
                if (position == mCurrentMembers.size() - 2) {
                    imageView.setImageResource(R.drawable.bg_room_info_add_btn);
                }
                if (position == mCurrentMembers.size() - 1) {
                    imageView.setImageResource(R.drawable.bg_room_info_minus_btn);
                }
            } else {
                MucRoomMember mMucRoomMember = mCurrentMembers.get(position);
                String name;
                if (role == 1) {// 群主 群内备注>好友备注>群内昵称
                    if (!TextUtils.isEmpty(mMucRoomMember.getRemarkName())) {
                        name = mMucRoomMember.getRemarkName();
                    } else {
                        if (mRemarksMap.containsKey(mCurrentMembers.get(position).getUserId())) {// 群组内 我的好友 显示 我对他备注的名字
                            name = mRemarksMap.get(mMucRoomMember.getUserId());
                        } else {
                            name = mMucRoomMember.getNickName();
                        }
                    }
                }else {
                    if (mRemarksMap.containsKey(mCurrentMembers.get(position).getUserId())) {// 群组内 我的好友 显示 我对他备注的名字
                        name = mRemarksMap.get(mMucRoomMember.getUserId());
                    } else {
                        name = mMucRoomMember.getNickName();

                    }
                }
                AvatarHelper.getInstance().displayAvatar(name, mMucRoomMember.getUserId(), imageView, true);
                memberName.setText(name);
                if (mMucRoomMember.getRole() == 1){
                    ivFrame.setImageResource(R.mipmap.frame_group_owner);
                    ivFrame.setVisibility(View.VISIBLE);
                }else if(mMucRoomMember.getRole() == 2){
                    ivFrame.setImageResource(R.mipmap.frame_group_manager);
                    ivFrame.setVisibility(View.VISIBLE);
                }else{
                    ivFrame.setVisibility(View.GONE);
                }
            }
            return convertView;
        }
    }

    class GridViewHolder {
        ImageView imageView;
        TextView memberName;
        ImageView ivFrame;

        GridViewHolder(View itemView) {
            imageView = itemView.findViewById(R.id.content);
            memberName = itemView.findViewById(R.id.member_name);
            ivFrame = itemView.findViewById(R.id.ivroleFrame);
        }
    }
}
