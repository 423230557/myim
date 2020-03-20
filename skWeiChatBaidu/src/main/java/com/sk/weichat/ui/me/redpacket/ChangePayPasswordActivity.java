package com.sk.weichat.ui.me.redpacket;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.gms.common.util.SharedPreferencesUtils;
import com.jungly.gridpasswordview.GridPasswordView;
import com.sk.weichat.MyApplication;
import com.sk.weichat.R;
import com.sk.weichat.Reporter;
import com.sk.weichat.helper.DialogHelper;
import com.sk.weichat.ui.base.BaseActivity;
import com.sk.weichat.util.Constants;
import com.sk.weichat.util.IdCardUtil;
import com.sk.weichat.util.Md5Util;
import com.sk.weichat.util.PreferenceUtils;
import com.sk.weichat.util.ToastUtil;
import com.xuan.xuanhttplibrary.okhttp.HttpUtils;
import com.xuan.xuanhttplibrary.okhttp.callback.BaseCallback;
import com.xuan.xuanhttplibrary.okhttp.result.ObjectResult;
import com.xuan.xuanhttplibrary.okhttp.result.Result;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;

public class ChangePayPasswordActivity extends BaseActivity {

    private boolean needOldPassword = true;
    private boolean needTwice = true;

    private SharedPreferences mSp ;
    private String oldPayPassword;
    private String newPayPassword;

    private TextView tvTip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_change_pay_password);

        initActionBar();
        initView();
        initData();
    }

    private void initData() {
        String userId = coreManager.getSelf().getUserId();
        if (TextUtils.isEmpty(userId)) {
            ToastUtil.showToast(this, R.string.tip_no_user_id);
            finish();
            return;
        }
        // 如果没有设置过支付密码，就不需要输入旧密码，
        needOldPassword = PreferenceUtils.getBoolean(this, Constants.IS_PAY_PASSWORD_SET + userId, true);
        Log.d(TAG, "initData: needOldPassword = " + needOldPassword);
        TextView tvTitle = findViewById(R.id.tv_title_center);
        TextView tvAction = findViewById(R.id.tvAction);
        EditText et_realname = findViewById(R.id.realname);
        EditText et_realnameID = findViewById(R.id.realnameID);
        ((TextView) findViewById(R.id.tv_title_center)).setText(getString(R.string.change_password));
        ((TextView) findViewById(R.id.tvAction)).setText(getString(R.string.btn_set_pay_password));
         mSp = this.getSharedPreferences("realname", MODE_PRIVATE);

        //找到控件

        //在config.xml文件中取出数据，然后显示到EditText上
        String realnameStr = mSp.getString("realname", "");
        String realnameIDStr = mSp.getString("realnameID", "");
        et_realname.setText(realnameStr);
        et_realnameID.setText(realnameIDStr);
        InputFilter typeFilter = new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                Pattern p;
                if (source.toString().contains("·") || source.toString().contains("•")) {
                    p = Pattern.compile("^[\\u4e00-\\u9fa5]+[·•][\\u4e00-\\u9fa5]+$");
                }else{
                   p = Pattern.compile("^[\\u4e00-\\u9fa5]+$");
                }
                Matcher m = p.matcher(source.toString());
                if (!m.matches()) return "";
                return null;
            }
        };

        et_realname.setFilters(new InputFilter[]{typeFilter});


        if (!needOldPassword) {
            // 如果不需要旧密码，直接传空字符串，
            oldPayPassword = "";
            tvTip.setText(R.string.tip_change_pay_password_input_new);
            tvTitle.setText(R.string.btn_set_pay_password);
            tvAction.setText(R.string.btn_set_pay_password);
        } else {
            tvTitle.setText(R.string.btn_change_pay_password);
            tvAction.setText(R.string.btn_change_pay_password);
        }
    }

    private void initView() {
        tvTip = findViewById(R.id.tvTip);
        final TextView tvFinish = findViewById(R.id.tvFinish);
        tvFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DialogHelper.showDefaulteMessageProgressDialog(ChangePayPasswordActivity.this);
                String realnameStr = ((EditText)  findViewById(R.id.realname) ).getText().toString();
                String realnameIDStr = ((EditText)  findViewById(R.id.realnameID) ).getText().toString();
                if(! IdCardUtil.isValidatedAllIdcard(realnameIDStr) ){
                    ToastUtil.showToast(ChangePayPasswordActivity.this, R.string.tip_change_pay_password_success);
                    return;
                }
                SharedPreferences.Editor editor = mSp.edit();
                //在config.xml文件中取出数据，然后显示到EditText上
                 editor.putString("realname", realnameStr);
                 editor.putString("realnameID", realnameIDStr);

                HttpUtils.get().url(coreManager.getConfig().UPDATE_PAY_PASSWORD)
                        .params("access_token", coreManager.getSelfStatus().accessToken)
                        .params("oldPayPassword", Md5Util.toMD5(oldPayPassword))
                        .params("payPassword", Md5Util.toMD5(newPayPassword))
                        .build()
                        .execute(new BaseCallback<Void>(Void.class) {
                            @Override
                            public void onResponse(ObjectResult<Void> result) {
                                DialogHelper.dismissProgressDialog();
                                if (Result.checkSuccess(ChangePayPasswordActivity.this, result)) {
                                    // 成功，
                                    ToastUtil.showToast(ChangePayPasswordActivity.this, R.string.tip_change_pay_password_success);
                                    // 记录下支付密码已经设置，
                                    MyApplication.getInstance().initPayPassword(coreManager.getSelf().getUserId(), 1);
                                }
                                finish();
                            }

                            @Override
                            public void onError(Call call, Exception e) {
                                Reporter.post("修改支付密码接口调用失败，", e);
                                DialogHelper.dismissProgressDialog();
                                String reason = e.getMessage();
                                if (TextUtils.isEmpty(reason)) {
                                    // 提示网络异常，
                                    reason = getString(R.string.net_exception);
                                }
                                ToastUtil.showToast(ChangePayPasswordActivity.this, reason);
                                finish();
                            }
                        });
            }
        });
        final GridPasswordView gpvPassword = findViewById(R.id.gpvPassword);
        gpvPassword.setOnPasswordChangedListener(new GridPasswordView.OnPasswordChangedListener() {
            @Override
            public void onTextChanged(String psw) {
                tvFinish.setVisibility(View.GONE);
            }

            @Override
            public void onInputFinish(String psw) {
                if (needOldPassword) {
                    oldPayPassword = psw;
                    DialogHelper.showDefaulteMessageProgressDialog(ChangePayPasswordActivity.this);
                    HttpUtils.get().url(coreManager.getConfig().UPDATE_PAY_PASSWORD)
                            .params("access_token", coreManager.getSelfStatus().accessToken)
                            .params("oldPayPassword", Md5Util.toMD5(oldPayPassword))
                            .build()
                            .execute(new BaseCallback<Void>(Void.class) {
                                @Override
                                public void onResponse(ObjectResult<Void> result) {
                                    DialogHelper.dismissProgressDialog();
                                    gpvPassword.clearPassword();
                                    if (Result.checkSuccess(ChangePayPasswordActivity.this, result)) {
                                        needOldPassword = false;
                                        tvTip.setText(R.string.tip_change_pay_password_input_new);
                                    }
                                }

                                @Override
                                public void onError(Call call, Exception e) {
                                    Reporter.post("修改支付密码接口调用失败，", e);
                                    DialogHelper.dismissProgressDialog();
                                    String reason = e.getMessage();
                                    if (TextUtils.isEmpty(reason)) {
                                        // 提示网络异常，
                                        reason = getString(R.string.net_exception);
                                    }
                                    ToastUtil.showToast(ChangePayPasswordActivity.this, reason);
                                    finish();
                                }
                            });
                } else if (needTwice) {
                    needTwice = false;
                    newPayPassword = psw;
                    gpvPassword.clearPassword();
                    tvTip.setText(R.string.tip_change_pay_password_input_twice);
                } else if (psw.equals(newPayPassword)) {
                    // 二次确认成功，
                    tvFinish.setVisibility(View.VISIBLE);
                } else {
                    // 二次确认失败，重新输入新密码，
                    gpvPassword.clearPassword();
                    needTwice = true;
                    tvTip.setText(R.string.tip_change_pay_password_input_incorrect);
                    tvFinish.setVisibility(View.GONE);
                }
            }
        });
    }

    private void initActionBar() {
        getSupportActionBar().hide();
        findViewById(R.id.iv_title_left).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        TextView mTvTitle = (TextView) findViewById(R.id.tv_title_center);
        mTvTitle.setText(getString(R.string.change_password));
    }
}
