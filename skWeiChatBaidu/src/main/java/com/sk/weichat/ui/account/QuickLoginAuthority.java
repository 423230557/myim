package com.sk.weichat.ui.account;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sk.weichat.R;
import com.sk.weichat.helper.LoginHelper;
import com.sk.weichat.ui.base.BaseActivity;
import com.sk.weichat.ui.share.ShareConstant;
import com.sk.weichat.ui.share.ShareLoginActivity;
import com.sk.weichat.ui.tool.WebViewActivity;
import com.sk.weichat.util.Constants;
import com.sk.weichat.util.PreferenceUtils;
import com.xuan.xuanhttplibrary.okhttp.HttpUtils;
import com.xuan.xuanhttplibrary.okhttp.callback.JsonCallback;

import okhttp3.Call;

public class QuickLoginAuthority extends BaseActivity {
    private static final String TAG = "QuickLoginAuthority";
    private String mShareContent;
    private boolean isNeedExecuteLogin;
    private String mUrlContent;

    public QuickLoginAuthority() {
        noLoginRequired();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_result);

        // 已进入授权界面
        ShareConstant.IS_SHARE_Q_COME = true;

        mShareContent = getIntent().getStringExtra(ShareConstant.EXTRA_SHARE_CONTENT);

        mUrlContent = getIntent().getData().toString();
        Log.e(TAG, "onCreate: " + mUrlContent);

        // 判断本地登录状态
        int userStatus = LoginHelper.prepareUser(mContext, coreManager);
        switch (userStatus) {
            case LoginHelper.STATUS_USER_FULL:
            case LoginHelper.STATUS_USER_NO_UPDATE:
            case LoginHelper.STATUS_USER_TOKEN_OVERDUE:
                boolean isConflict = PreferenceUtils.getBoolean(this, Constants.LOGIN_CONFLICT, false);
                if (isConflict) {
                    isNeedExecuteLogin = true;
                }
                break;
            case LoginHelper.STATUS_USER_SIMPLE_TELPHONE:
                isNeedExecuteLogin = true;
                break;
            case LoginHelper.STATUS_NO_USER:
            default:
                isNeedExecuteLogin = true;
        }

        if (isNeedExecuteLogin) {// 需要先执行登录操作
            startActivity(new Intent(mContext, ShareLoginActivity.class));
            finish();
            return;
        }
        initActionBar();
        initView();
    }

    private void initActionBar() {
        getSupportActionBar().hide();
        findViewById(R.id.iv_title_left).setVisibility(View.GONE);
        TextView mTvTitleLeft = findViewById(R.id.tv_title_left);
        mTvTitleLeft.setText(getString(R.string.close));
        TextView tvTitle = (TextView) findViewById(R.id.tv_title_center);
        tvTitle.setText(getString(R.string.centent_bar, getString(R.string.app_name)));
        mTvTitleLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    private void initView() {
        ImageView mAppIconIv = findViewById(R.id.app_icon_iv);
        TextView mAppNameTv = findViewById(R.id.app_name_tv);

        //授权登录按钮
        findViewById(R.id.login_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAuthLogin(mUrlContent);
            }
        });
    }

    /**
     * 授权登录
     *
     * @param url
     */
    private void onAuthLogin(String url) {
        Log.e("onResponse", "onAuthLogin: " + url);

        String appId = WebViewActivity.URLRequest(url).get("appId");
        String redirectURL = WebViewActivity.URLRequest(url).get("callbackUrl");
        String accessToken = coreManager.getSelfStatus().accessToken;
        Log.e("onResponse", "onAuthLogin: " + accessToken);
        String urlxc = coreManager.getConfig().AUTHOR_CHECK;

        //  HttpUtils.get().url("http://192.168.0.141:8092/open/codeAuthorCheck")
        HttpUtils.get().url(coreManager.getConfig().AUTHOR_CHECK)
                .params("appId", appId)
                .params("state", accessToken)
                .params("callbackUrl", redirectURL)
                .build().execute(new JsonCallback() {
            @Override
            public void onResponse(String result) {

                JSONObject json = JSON.parseObject(result);
                Log.e("onResponse", "onResponse: " + result);
                int code = json.getIntValue("resultCode");
                if (1 == code) {
                    String html = json.getJSONObject("data").getString("callbackUrl") + "?code=" + json.getJSONObject("data").getString("code");
                    Uri uri = Uri.parse(html);

                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);

                    startActivity(intent);
                }
            }

            @Override
            public void onError(Call call, Exception e) {

            }
        });
    }
}

