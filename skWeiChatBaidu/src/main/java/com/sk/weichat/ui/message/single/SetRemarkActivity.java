package com.sk.weichat.ui.message.single;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.sk.weichat.AppConstant;
import com.sk.weichat.R;
import com.sk.weichat.bean.Friend;
import com.sk.weichat.broadcast.CardcastUiUpdateUtil;
import com.sk.weichat.broadcast.MsgBroadcast;
import com.sk.weichat.db.dao.FriendDao;
import com.sk.weichat.helper.DialogHelper;
import com.sk.weichat.ui.base.BaseActivity;
import com.xuan.xuanhttplibrary.okhttp.HttpUtils;
import com.xuan.xuanhttplibrary.okhttp.callback.BaseCallback;
import com.xuan.xuanhttplibrary.okhttp.result.ObjectResult;
import com.xuan.xuanhttplibrary.okhttp.result.Result;

import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;

public class SetRemarkActivity extends BaseActivity implements View.OnClickListener {
    public static final String ACTION_SET_REMARK = "ACTION_SET_REMARK";
    private EditText mRemarkNameEdit;

    private String mLoginUserId;
    private String mFriendId;
    @Nullable
    private Friend mFriend;
    private TextView tvRight;
    private EditText etDescribe;
    private View rlLabel;

    public static void start(Context ctx, String friendId) {
        Intent intent = new Intent(ctx, SetRemarkActivity.class);
        intent.putExtra(AppConstant.EXTRA_USER_ID, friendId);
        ctx.startActivity(intent);
    }

    public static void startForResult(Activity ctx, String friendId, int requestCode) {
        Intent intent = new Intent(ctx, SetRemarkActivity.class);
        intent.putExtra(AppConstant.EXTRA_USER_ID, friendId);
        ctx.startActivityForResult(intent, requestCode);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_remark);
        mLoginUserId = coreManager.getSelf().getUserId();
        mFriendId = getIntent().getStringExtra(AppConstant.EXTRA_USER_ID);
        mFriend = FriendDao.getInstance().getFriend(mLoginUserId, mFriendId);
        initActionBar();
        initView();
        MyTextWatcher myTextWatcher = new MyTextWatcher();
        mRemarkNameEdit.addTextChangedListener(myTextWatcher);
        etDescribe.addTextChangedListener(myTextWatcher);
        rlLabel.setOnClickListener(v -> {
            Intent intentLabel = new Intent(this, SetLabelActivity.class);
            intentLabel.putExtra(AppConstant.EXTRA_USER_ID, mFriendId);
            startActivity(intentLabel);
        });
    }

    private void initActionBar() {
        getSupportActionBar().hide();
        findViewById(R.id.iv_title_left).setOnClickListener(this);
        tvRight = (TextView) findViewById(R.id.tv_title_right);
        tvRight.setEnabled(false);
        tvRight.setText(R.string.finish);
        tvRight.setOnClickListener(this);
        TextView tvTitle = (TextView) findViewById(R.id.tv_title_center);
        tvTitle.setText(R.string.set_remark_and_label);
    }

    private void initView() {
        rlLabel = findViewById(R.id.rlLabel);
        etDescribe = (EditText) findViewById(R.id.etDescribe);
        mRemarkNameEdit = (EditText) findViewById(R.id.department_edit);
        if (!TextUtils.isEmpty(currentRemarkName())) {
            mRemarkNameEdit.setText(currentRemarkName());
        }
        if (!TextUtils.isEmpty(currentDescribe())) {
            etDescribe.setText(currentDescribe());
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.iv_title_left:
                finish();
                break;
            case R.id.tv_title_right:
                remarkFriend();
                break;
        }
    }

    @Nullable
    private String currentRemarkName() {
        if (mFriend == null) {
            return null;
        }
        return mFriend.getRemarkName();
    }

    @Nullable
    private String currentDescribe() {
        if (mFriend == null) {
            return null;
        }
        return mFriend.getDescribe();
    }

    private void remarkFriend() {
        String remarkName = mRemarkNameEdit.getText().toString();
        String describe = etDescribe.getText().toString();
        // 现在备注可以为空，表示不设置备注，
        Map<String, String> params = new HashMap<>();
        params.put("access_token", coreManager.getSelfStatus().accessToken);
        params.put("toUserId", mFriendId);
        params.put("remarkName", remarkName);
        params.put("describe", describe);
        DialogHelper.showDefaulteMessageProgressDialog(this);

        HttpUtils.get().url(coreManager.getConfig().FRIENDS_REMARK)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        if (Result.checkSuccess(SetRemarkActivity.this, result)) {
                            FriendDao.getInstance().updateRemarkNameAndDescribe(
                                    mLoginUserId, mFriendId, remarkName, describe);
                            MsgBroadcast.broadcastMsgUiUpdate(mContext);
                            CardcastUiUpdateUtil.broadcastUpdateUi(mContext);
                            Intent intent = new Intent(com.sk.weichat.broadcast.OtherBroadcast.NAME_CHANGE);
                            intent.putExtra("remarkName", remarkName);
                            intent.putExtra("describe", describe);
                            sendBroadcast(intent);
                            // 备注和描述通过result传给用户信息页而不是广播，避免用户信息页重复发广播，
                            setResult(Activity.RESULT_OK, intent);
                            finish();
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                        Toast.makeText(mContext, R.string.tip_change_remark_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private class MyTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            if (mFriend == null) {
                // 陌生人只要备注和描述有一个非空就可以提交更新，
                tvRight.setEnabled(
                        !TextUtils.isEmpty(mRemarkNameEdit.getText())
                                || !TextUtils.isEmpty(etDescribe.getText())
                );
                return;
            }
            // 好友只要备注和描述有一个修改了就可以提交更新，
            tvRight.setEnabled(
                    !TextUtils.equals(currentDescribe(), etDescribe.getText())
                            || !TextUtils.equals(currentRemarkName(), mRemarkNameEdit.getText())
            );
        }
    }
}
