package com.builtbydeep.sovra;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayDeque;
import java.util.Deque;

public final class MainActivity extends AppCompatActivity {
    private static final String CRASH_PREFS = "sp5";
    private static final String CRASH_KEY = "last_crash";
    private static boolean crashHandlerInstalled;

    private ViewPager2 viewPager;
    private BottomNavigationView navigation;
    private final Deque<Integer> backHistory = new ArrayDeque<>();
    private boolean selectingFromPager;
    private int pendingBackSelection = -1;
    private int currentPageBeforeSelection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        installCrashHandler(getApplicationContext());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.main_root);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(left + insets.left, top + insets.top,
                    right + insets.right, bottom + insets.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);

        viewPager = findViewById(R.id.view_pager);
        navigation = findViewById(R.id.bottom_navigation);
        viewPager.setAdapter(new Pages(this));
        viewPager.setOffscreenPageLimit(3);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position != currentPageBeforeSelection) {
                    if (position != pendingBackSelection) {
                        backHistory.addLast(currentPageBeforeSelection);
                    }
                    pendingBackSelection = -1;
                    currentPageBeforeSelection = position;
                }
                selectingFromPager = true;
                navigation.setSelectedItemId(menuId(position));
                selectingFromPager = false;
            }
        });
        currentPageBeforeSelection = 0;
        navigation.setSelectedItemId(R.id.nav_generate);
        navigation.setOnItemSelectedListener(this::onNavigationSelected);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!backHistory.isEmpty()) {
                    int target = backHistory.removeLast();
                    pendingBackSelection = target;
                    viewPager.setCurrentItem(target, false);
                } else if (viewPager.getCurrentItem() != 0) {
                    pendingBackSelection = 0;
                    viewPager.setCurrentItem(0, false);
                    backHistory.clear();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private static synchronized void installCrashHandler(Context context) {
        if (crashHandlerInstalled) return;
        crashHandlerInstalled = true;

        Context applicationContext = context.getApplicationContext();
        Thread.UncaughtExceptionHandler previousHandler =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            String stackTrace = Log.getStackTraceString(throwable);
            try {
                boolean saved = applicationContext.getSharedPreferences(
                                CRASH_PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putString(CRASH_KEY, stackTrace)
                        .commit();
                if (!saved) {
                    Log.e("SovraCrashHandler", "Unable to persist the crash report");
                }

                Intent debugIntent = new Intent(applicationContext,
                        com.builtbydeep.sovra.system.DebugActivity.class);
                debugIntent.putExtra(
                        com.builtbydeep.sovra.system.DebugActivity.EXTRA_STACK_TRACE, stackTrace);
                debugIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                applicationContext.startActivity(debugIntent);
            } catch (Throwable reportingFailure) {
                Log.e("SovraCrashHandler", "Unable to open the crash report screen",
                        reportingFailure);
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
    }

    private boolean onNavigationSelected(MenuItem item) {
        if (selectingFromPager) {
            return true;
        }
        int target = pageForMenuId(item.getItemId());
        if (target >= 0 && target != viewPager.getCurrentItem()) {
            viewPager.setCurrentItem(target, false);
        }
        return target >= 0;
    }

    private static int pageForMenuId(int id) {
        if (id == R.id.nav_generate) return 0;
        if (id == R.id.nav_models) return 1;
        if (id == R.id.nav_library) return 2;
        if (id == R.id.nav_settings) return 3;
        return -1;
    }

    private static int menuId(int page) {
        if (page == 1) return R.id.nav_models;
        if (page == 2) return R.id.nav_library;
        if (page == 3) return R.id.nav_settings;
        return R.id.nav_generate;
    }

    private static final class Pages extends FragmentStateAdapter {
        Pages(AppCompatActivity activity) {
            super(activity);
        }

        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 1:
                    return new ModelsFragmentActivity();
                case 2:
                    return new LibraryFragmentActivity();
                case 3:
                    return new SettingFragmentActivity();
                default:
                    return new GenerateFragmentActivity();
            }
        }

        @Override
        public int getItemCount() {
            return 4;
        }
    }
}
