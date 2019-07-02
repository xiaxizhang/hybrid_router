// MIT License
// -----------

// Copyright (c) 2019 WeiDian Group
// Permission is hereby granted, free of charge, to any person
// obtaining a copy of this software and associated documentation
// files (the "Software"), to deal in the Software without
// restriction, including without limitation the rights to use,
// copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the
// Software is furnished to do so, subject to the following
// conditions:

// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.

// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
// OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
// HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
// WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
// FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
// OTHER DEALINGS IN THE SOFTWARE.
package com.vdian.flutter.hybridrouter.page;

import android.app.Activity;
import android.arch.lifecycle.Lifecycle;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.vdian.flutter.hybridrouter.FlutterManager;
import com.vdian.flutter.hybridrouter.FlutterStackManagerUtil;
import com.vdian.flutter.hybridrouter.HybridRouterPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.flutter.embedding.engine.FlutterShellArgs;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.view.FlutterMain;
import io.flutter.view.FlutterNativeView;
import io.flutter.view.FlutterRunArguments;
import io.flutter.view.FlutterView;

import static android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW;

/**
 * ┏┛ ┻━━━━━┛ ┻┓
 * ┃　　　　　　 ┃
 * ┃　　　━　　　┃
 * ┃　┳┛　  ┗┳　┃
 * ┃　　　　　　 ┃
 * ┃　　　┻　　　┃
 * ┃　　　　　　 ┃
 * ┗━┓　　　┏━━━┛
 * * ┃　　　┃   神兽保佑
 * * ┃　　　┃   代码无BUG！
 * * ┃　　　┗━━━━━━━━━┓
 * * ┃　　　　　　　    ┣┓
 * * ┃　　　　         ┏┛
 * * ┗━┓ ┓ ┏━━━┳ ┓ ┏━┛
 * * * ┃ ┫ ┫   ┃ ┫ ┫
 * * * ┗━┻━┛   ┗━┻━┛
 *
 * @author qigengxin
 * @since 2019-06-19 10:23
 */
public class FlutterWrapFragment extends Fragment implements IFlutterNativePage {

    public interface IPageDelegate {

        /**
         * flutter 请求结束 native 页面
         */
        void finishNativePage(IFlutterNativePage page, @Nullable Object result);

        /**
         * 判断是否是 tab
         * @return
         */
        boolean isTab(IFlutterNativePage page);
    }

    public static class Builder {

        IPageDelegate delegate;
        Bundle arguments = new Bundle();

        public Builder pageDelegate(IPageDelegate delegate) {
            this.delegate = delegate;
            return this;
        }

        public Builder route(FlutterRouteOptions routeOptions) {
            if (routeOptions != null) {
                arguments.putParcelable(EXTRA_FLUTTER_ROUTE, routeOptions);
            }
            return this;
        }

        public Builder appBundlePath(String appBundlePath) {
            if (appBundlePath != null) {
                arguments.putString(EXTRA_APP_BUNDLE_PATH, appBundlePath);
            }
            return this;
        }

        public Builder dartEntrypoint(String dartEntrypoint) {
            if (dartEntrypoint != null) {
                arguments.putString(EXTRA_DART_ENTRYPOINT, dartEntrypoint);
            }
            return this;
        }

        public Builder extra(Bundle arguments) {
            this.arguments.putAll(arguments);
            return this;
        }

        public FlutterWrapFragment build() {
            FlutterWrapFragment ret = new FlutterWrapFragment();
            ret.delegate = delegate;
            ret.setArguments(arguments);
            return ret;
        }
    }

    public static final String EXTRA_RESULT_KEY = "ext_result_key";
    public static final String EXTRA_FLUTTER_ROUTE = "ext_flutter_route";
    public static final String EXTRA_INITIALIZATION_ARGS = "ext_initialization_args";
    public static final String EXTRA_APP_BUNDLE_PATH = "ext_app_bundle_path";
    public static final String EXTRA_DART_ENTRYPOINT = "ext_dart_entrypoint";

    private static int FLAG_ATTACH = 1;
    private static int FLAG_ENGINE_INIT = 2;
    private static int MAX_REQUEST_CODE = 100;

    @Override
    public boolean isAttachToFlutter() {
        return hasFlag(flag, FLAG_ATTACH);
    }

    @Override
    public void attachFlutter() {
        localAttachFlutter();
    }

    @Override
    public void detachFlutter() {
        localDetachFlutter();
    }

    @Override
    public void requestDetachFromWindow() {
        detachFlutter();
    }

    @Override
    public void openNativePage(@NonNull NativeRouteOptions routeOptions, @NonNull MethodChannel.Result result) {
        onNativePageRoute(routeOptions, generateRequestCodeByChannel(result));
    }


    @Override
    public void openFlutterPage(@NonNull FlutterRouteOptions routeOptions, @NonNull MethodChannel.Result result) {
        onFlutterPageRoute(routeOptions, generateRequestCodeByChannel(result));
    }

    @Override
    public void finishNativePage(@Nullable Object result) {
        // 先让 flutter 脱离渲染
        detachFlutter();
        if (delegate != null) {
            delegate.finishNativePage(this, result);
        } else {
            // 结束当前 native 页面
            FragmentManager fm = getFragmentManager();
            if (fm != null) {
                fm.beginTransaction()
                        .remove(this)
                        .commit();
            }
        }
    }

    @Override
    public String getNativePageId() {
        return nativePageId;
    }

    /**
     * 获取初始路由，如果是首次打开页面，需要通过 flutter 来主动获取路由信息
     */
    @Override
    public Map<String, Object> getInitRoute() {
        Map<String, Object> ret = new HashMap<>();
        if (routeOptions != null) {
            ret.put("pageName", routeOptions.pageName);
            if (routeOptions.args != null) {
                ret.put("args", routeOptions.args);
            }
        }
        ret.put("nativePageId", nativePageId);
        return ret;
    }

    @Override
    public int generateRequestCodeByChannel(MethodChannel.Result result) {
        int requestCode = generateRequestCode();
        if (requestCode < 0) {
            result.error("-1", "Not enough request code, Fuck what??, map size:"
                    + resultChannelMap.size(), null);
            return requestCode;
        }
        resultChannelMap.put(requestCode, result);
        return requestCode;
    }

    @Override
    public int generateRequestCodeByCallback(@NonNull IPageResultCallback callback) {
        int requestCode = generateRequestCode();
        if (requestCode < 0) {
            callback.onPageResult(requestCode, Activity.RESULT_CANCELED, null);
        }
        pageCallbackMap.put(requestCode, callback);
        return requestCode;
    }

    @Override
    public void onFlutterRouteEvent(String name, int eventId, Map extra) {
    }

    @Override
    public boolean isTab() {
        if (delegate != null) {
            return delegate.isTab(this);
        }
        return false;
    }

    // 当前页面的 id
    protected final String nativePageId = FlutterManager.getInstance().generateNativePageId();
    /**
     * 当前 flutter 页面的 page name
     */
    protected FlutterRouteOptions routeOptions;
    @Nullable
    protected FlutterNativeView flutterNativeView;
    @Nullable
    protected FlutterView flutterView;
    protected FrameLayout container;
    protected View maskView;
    // 是否是首次启动 flutter engine
    protected boolean isCreatePage;
    protected SparseArray<MethodChannel.Result> resultChannelMap = new SparseArray<>();
    protected SparseArray<IPageResultCallback> pageCallbackMap = new SparseArray<>();
    // 当前 flutter 的状态
    protected int flag;
    // 当前 fragment 不能处理的 delegate，比如页面结束，tab 判断，native 跳转动画
    protected IPageDelegate delegate;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // push the page to stack manager
        FlutterManager.getInstance().addNativePage(this);
        IFlutterWrapConfig wrapConfig = FlutterManager.getInstance().getFlutterWrapConfig();
        if (routeOptions == null) {
            // 理由参数获取
            if (savedInstanceState != null) {
                routeOptions = savedInstanceState.getParcelable(EXTRA_FLUTTER_ROUTE);
            } else {
                Bundle arguments = getArguments();
                if (arguments != null && arguments.containsKey(EXTRA_FLUTTER_ROUTE)) {
                    routeOptions = arguments.getParcelable(EXTRA_FLUTTER_ROUTE);
                } else if (wrapConfig != null) {
                    routeOptions = wrapConfig.parseFlutterRouteFromBundle(this, getArguments());
                }
            }
        }
        if (routeOptions == null) {
            // 如果没有路由信息，默认打开根路径
            routeOptions = FlutterRouteOptions.home;
        }
        if (FlutterManager.getInstance().getFlutterEngine() == null) {
            isCreatePage = true;
        }
        // init flutter
        try {
            Context context = getContext();
            assert context != null;
            initializeFlutter(context);
            setupFlutterEngine(context);
            assert flutterNativeView != null;
            flag |= FLAG_ENGINE_INIT;
        } catch (Throwable t) {
            // engine init error
            t.printStackTrace();
            onFlutterInitFailure(t);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (!hasFlag(flag, FLAG_ENGINE_INIT)) {
            return null;
        }
        assert flutterNativeView != null;
        Context context = getContext();
        assert context != null;
        this.container = new FrameLayout(context);
        this.container.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT
                , ViewGroup.LayoutParams.MATCH_PARENT));
        maskView = createMaskView(this.container);
        attachFlutter();
        if (isCreatePage) {
            onRegisterPlugin(flutterNativeView.getPluginRegistry());
        }
        return this.container;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (hasFlag(flag, FLAG_ENGINE_INIT)) {
            attachFlutter();
            if (flutterView != null) {
                flutterView.onStart();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasFlag(flag, FLAG_ENGINE_INIT)) {
            attachFlutter();
            innerPreFlutterApplyTheme();
            if (flutterView != null) {
                flutterView.onPostResume();
            }
            innerPostFlutterApplyTheme();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (hasFlag(flag, FLAG_ENGINE_INIT)) {
            if (flutterView != null) {
                flutterView.onPause();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (hasFlag(flag, FLAG_ENGINE_INIT)) {
            if (flutterView != null) {
                flutterView.onStop();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        detachFlutter();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cancelAllResult();
        FlutterManager.getInstance().removeNativePage(this);
        detachFlutter();
        if (HybridRouterPlugin.isRegistered()) {
            HybridRouterPlugin.getInstance().onNativePageFinished(nativePageId);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (routeOptions != null) {
            outState.putParcelable(EXTRA_FLUTTER_ROUTE, routeOptions);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (flutterNativeView != null) {
            flutterNativeView.getPluginRegistry().onActivityResult(requestCode, resultCode, data);
        }
        sendResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (flutterNativeView != null) {
            flutterNativeView.getPluginRegistry().onRequestPermissionsResult(requestCode, permissions,
                    grantResults);
        } else {
            Log.w("FlutterWrapFragment",
                    "onRequestPermissionResult() invoked before FlutterFragment was attached to an Activity.");
        }
    }

    public void onNewIntent(@NonNull Intent intent) {
        if (flutterNativeView != null) {
            flutterNativeView.getPluginRegistry().onNewIntent(intent);
        } else {
            Log.w("FlutterWrapFragment",
                    "onNewIntent() invoked before FlutterFragment was attached to an Activity.");
        }
    }


    public void onUserLeaveHint() {
        if (this.flutterNativeView != null) {
            this.flutterNativeView.getPluginRegistry().onUserLeaveHint();
        } else {
            Log.w("FlutterFragment", "onUserLeaveHint() invoked before FlutterFragment was attached to an Activity.");
        }

    }

    public void onTrimMemory(int level) {
        if (this.flutterView != null) {
            if (level == TRIM_MEMORY_RUNNING_LOW) {
                this.flutterView.onMemoryPressure();
            }
        } else {
            Log.w("FlutterFragment", "onTrimMemory() invoked before FlutterFragment was attached to an Activity.");
        }

    }

    public void onLowMemory() {
        super.onLowMemory();
        if (this.flutterView != null) {
            this.flutterView.onMemoryPressure();
        }
    }

    protected void initializeFlutter(@NonNull Context context) {
        Bundle arguments = getArguments();
        String[] flutterShellArgsArray = null;
        if (arguments != null) {
            flutterShellArgsArray = arguments.getStringArray(EXTRA_INITIALIZATION_ARGS);
        }
        FlutterShellArgs flutterShellArgs = new FlutterShellArgs(flutterShellArgsArray == null
                ? new String[0] : flutterShellArgsArray);
        FlutterMain.startInitialization(context.getApplicationContext());
        FlutterMain.ensureInitializationComplete(context.getApplicationContext(),
                flutterShellArgs.toArray());
    }

    protected void doInitialFlutterViewRun() {
        assert flutterNativeView != null;
        if (!flutterNativeView.isApplicationRunning()) {
            FlutterRunArguments args = new FlutterRunArguments();
            args.bundlePath = getAppBundlePath();
            args.entrypoint = getEntrypoint();
            flutterNativeView.runFromBundle(args);
        }
    }

    protected void setupFlutterEngine(@NonNull Context context) {
        flutterNativeView = FlutterManager.getInstance().getOrCreateNativeView(context);
    }

    protected void onNativePageRoute(NativeRouteOptions routeOptions, int requestCode) {
        IFlutterWrapConfig wrapConfig = FlutterManager.getInstance().getFlutterWrapConfig();
        if (wrapConfig == null || !wrapConfig.onNativePageRoute(this,
                routeOptions, requestCode)) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(routeOptions.url));
            FlutterStackManagerUtil.updateIntent(intent, routeOptions.args);
            startActivityForResult(intent, requestCode);
        }
    }

    /**
     * 请求打开 新的 flutter page，借助新的 native page 容器
     *
     * @param routeOptions
     * @param requestCode
     */
    protected void onFlutterPageRoute(FlutterRouteOptions routeOptions, int requestCode) {
        IFlutterWrapConfig wrapConfig = FlutterManager.getInstance().getFlutterWrapConfig();
        if (wrapConfig == null || !wrapConfig.onFlutterPageRoute(this,
                routeOptions, requestCode)) {
            if (getContext() != null) {
                Intent intent = FlutterWrapActivity.startIntent(getContext(), routeOptions);
                startActivityForResult(intent, requestCode);
            } else {
                throw new IllegalStateException("The context of current fragment is null");
            }
        }
    }

    protected void onFlutterInitFailure(Throwable t) {
    }

    /**
     * 注册插件
     */
    protected void onRegisterPlugin(PluginRegistry pluginRegistry) {
        try {
            Class.forName("io.flutter.plugins.GeneratedPluginRegistrant")
                    .getDeclaredMethod("registerWith", PluginRegistry.class)
                    .invoke(null, pluginRegistry);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void preFlutterApplyTheme() {
    }

    protected void postFlutterApplyTheme() {
    }

    @NonNull
    protected FlutterView createFlutterView(ViewGroup container) {
        return new FlutterView(container.getContext(), null, flutterNativeView);
    }

    protected View createMaskView(ViewGroup container) {
        View background = new View(container.getContext());
        background.setBackground(getLaunchScreenDrawableFromActivityTheme());
        background.setClickable(true);
        return background;
    }

    /**
     * 配置flutter 页面
     *
     * @param needWaitForResult 是否需要等待 channel 结束后才显示页面
     * @return
     */
    protected MethodChannel.Result setupFlutterView(ViewGroup container, final FlutterView flutterView,
                                                    View maskView, boolean needWaitForResult) {
        // 这里先移除不是当前 flutter view 的 flutter view，因为 flutter view 只有在创建的时候才会 attach
        // 所以在 attach 的时候会重新创建
        for (int i = 0; i < container.getChildCount(); ++i) {
            View view = container.getChildAt(i);
            if (view instanceof FlutterView && view != flutterView) {
                container.removeViewAt(i);
            }
        }
        if (flutterView.getParent() != container) {
            // add flutter view to root view
            ViewGroup.LayoutParams lp = flutterView.getLayoutParams();
            lp = lp == null ? new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT
                    , ViewGroup.LayoutParams.MATCH_PARENT) : lp;
            container.addView(flutterView, lp);
        }
        if (maskView.getParent() != container) {
            ViewGroup.LayoutParams lp = maskView.getLayoutParams();
            lp = lp == null ? new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT
                    , ViewGroup.LayoutParams.MATCH_PARENT) : lp;
            container.addView(maskView, lp);
        }

        final AtomicInteger visibleCount = !needWaitForResult ?
                new AtomicInteger(1) : new AtomicInteger(0);
        final View finalMaskView = maskView;
        MethodChannel.Result result = new MethodChannel.Result() {
            @Override
            public void success(@Nullable Object o) {
                if (visibleCount.incrementAndGet() >= 2 && finalMaskView != null) {
                    finalMaskView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void error(String s, @Nullable String s1, @Nullable Object o) {
            }

            @Override
            public void notImplemented() {
            }
        };
        flutterView.addFirstFrameListener(new FlutterView.FirstFrameListener() {
            @Override
            public void onFirstFrame() {
                flutterView.removeFirstFrameListener(this);
                if (visibleCount.incrementAndGet() >= 2 && finalMaskView != null) {
                    finalMaskView.setVisibility(View.INVISIBLE);
                }
            }
        });
        // 此处修复 flutter 和 native 沉浸式同步的问题
        ViewCompat.requestApplyInsets(flutterView);
        return result;
    }

    /**
     * 发送 onActivityResult 获取到的结果
     */
    protected void sendResult(int requestCode, int resultCode, @Nullable Intent data) {
        MethodChannel.Result resultChannel = resultChannelMap.get(requestCode);
        if (resultChannel != null) {
            resultChannelMap.remove(requestCode);
            HashMap<String, Object> ret = new HashMap<>();
            ret.put("resultCode", resultCode);
            Object result = null;
            Bundle extras = data == null ? null : data.getExtras();
            if (extras != null && data.hasExtra(EXTRA_RESULT_KEY)) {
                // 从 flutter 过来的数据
                result = extras.get(EXTRA_RESULT_KEY);
            } else if (extras != null){
                HashMap<String, Object> retMap = new HashMap<>();
                if (data.getExtras() != null) {
                    for (String key : data.getExtras().keySet()) {
                        retMap.put(key, data.getExtras().get(key));
                    }
                }
                result = retMap;
            }
            ret.put("data", result);
            resultChannel.success(ret);
        }
        IPageResultCallback callback = pageCallbackMap.get(requestCode);
        if (callback != null) {
            pageCallbackMap.remove(requestCode);
            callback.onPageResult(requestCode, resultCode, data);
        }
    }

    /**
     * 结束所有的 result channel，如果当前页面有打开一个新的页面的请求，但是页面请求没有返回结果
     * 这里手动结束掉所有的 result 回调
     */
    protected void cancelAllResult() {
        for (int i = 0, size = resultChannelMap.size(); i < size; ++i) {
            MethodChannel.Result result = resultChannelMap.valueAt(i);
            HashMap<String, Object> ret = new HashMap<>();
            ret.put("resultCode", Activity.RESULT_CANCELED);
            result.success(ret);
        }
        resultChannelMap.clear();
        for (int i = 0, size = pageCallbackMap.size(); i < size; ++i) {
            IPageResultCallback callback = pageCallbackMap.valueAt(i);
            callback.onPageResult(pageCallbackMap.keyAt(i), Activity.RESULT_CANCELED, null);
        }
        pageCallbackMap.clear();
    }

    protected String getAppBundlePath() {
        Bundle arguments = getArguments();
        String appBundlePath = getContext() == null ? null
                : FlutterMain.findAppBundlePath(getContext().getApplicationContext());
        if (arguments != null) {
            appBundlePath = arguments.getString(EXTRA_APP_BUNDLE_PATH, appBundlePath);
        }
        return appBundlePath;
    }

    protected String getEntrypoint() {
        Bundle arguments = getArguments();
        String entrypoint = "main";
        if (arguments != null) {
            entrypoint = arguments.getString(EXTRA_DART_ENTRYPOINT, entrypoint);
        }
        return entrypoint;
    }

    private void innerPreFlutterApplyTheme() {
        IFlutterWrapConfig wrapConfig = FlutterManager.getInstance().getFlutterWrapConfig();
        if (wrapConfig != null) {
            wrapConfig.preFlutterApplyTheme(this);
        }
        preFlutterApplyTheme();
    }

    private void innerPostFlutterApplyTheme() {
        IFlutterWrapConfig wrapConfig = FlutterManager.getInstance().getFlutterWrapConfig();
        if (wrapConfig != null) {
            wrapConfig.postFlutterApplyTheme(this);
        }
        postFlutterApplyTheme();
    }

    private boolean hasFlag(int flag, int target) {
        return (flag & target) == target;
    }

    private int generateRequestCode() {
        if (resultChannelMap.size() == 0) {
            return 1;
        }
        int count = 0;
        int start = resultChannelMap.keyAt(resultChannelMap.size() - 1) + 1;
        while (count < MAX_REQUEST_CODE) {
            start = (start > MAX_REQUEST_CODE) ? (start - MAX_REQUEST_CODE) : start;
            if (resultChannelMap.get(start) == null) {
                // found request code
                return start;
            }
            count++;
        }
        // failure to generate
        return -1;
    }

    private void localAttachFlutter() {
        if (hasFlag(flag, FLAG_ATTACH) || !hasFlag(flag, FLAG_ENGINE_INIT)) {
            return;
        }
        flag |= FLAG_ATTACH;
        final IFlutterNativePage curNativePage = FlutterManager.getInstance().getCurNativePage();
        if (curNativePage != null) {
            curNativePage.detachFlutter();
        }
        assert container != null;
        assert maskView != null;
        // 设置当前页面是 native page
        FlutterManager.getInstance().setCurNativePage(this);
        assert flutterNativeView != null;
        if (flutterView == null) {
            flutterView = createFlutterView(container);
            if (FlutterManager.getInstance().isFlutterRouteStart()) {
                MethodChannel.Result result = setupFlutterView(container, flutterView, maskView,
                        true);
                // TODO add tab flag
                HybridRouterPlugin.getInstance().pushFlutterPager(routeOptions.pageName,
                        routeOptions.args, nativePageId, result, false);
            } else {
                setupFlutterView(container, flutterView, maskView, false);
            }
        }
        doInitialFlutterViewRun();
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            flutterView.onStart();
        }
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            flutterView.onPostResume();
        }
    }

    private void localDetachFlutter() {
        if (!hasFlag(flag, FLAG_ATTACH) || !hasFlag(flag, FLAG_ENGINE_INIT)) {
            return;
        }
        flag &= ~FLAG_ATTACH;
        assert flutterView != null;
        assert flutterNativeView != null;
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            flutterView.onPause();
        }
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            flutterView.onStop();
        }
        flutterView.getPluginRegistry().onViewDestroy(flutterView.getFlutterNativeView());
        flutterView.setMessageHandler("flutter/platform", null);
        flutterView.detach();
        flutterView = null;
    }

    @Nullable
    private Drawable getLaunchScreenDrawableFromActivityTheme() {
        Context context = getContext();
        if (context == null) return null;
        TypedValue typedValue = new TypedValue();
        if (!context.getTheme().resolveAttribute(
                android.R.attr.windowBackground,
                typedValue,
                true)) {
            return null;
        }
        if (typedValue.resourceId == 0) {
            return null;
        }
        try {
            return getResources().getDrawable(typedValue.resourceId);
        } catch (Resources.NotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}
