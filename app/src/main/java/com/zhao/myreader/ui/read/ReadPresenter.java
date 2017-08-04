package com.zhao.myreader.ui.read;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.ViewDragHelper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.SeekBar;

import com.scwang.smartrefresh.layout.api.RefreshLayout;
import com.scwang.smartrefresh.layout.listener.OnLoadmoreListener;
import com.zhao.myreader.R;
import com.zhao.myreader.application.MyApplication;
import com.zhao.myreader.application.SysManager;
import com.zhao.myreader.base.BaseActivity;
import com.zhao.myreader.base.BasePresenter;
import com.zhao.myreader.callback.ResultCallback;
import com.zhao.myreader.common.APPCONST;
import com.zhao.myreader.creator.DialogCreator;
import com.zhao.myreader.entity.Setting;
import com.zhao.myreader.enums.Language;
import com.zhao.myreader.enums.ReadStyle;
import com.zhao.myreader.greendao.entity.Book;
import com.zhao.myreader.greendao.entity.Chapter;
import com.zhao.myreader.greendao.service.BookService;
import com.zhao.myreader.greendao.service.ChapterService;
import com.zhao.myreader.util.BrightUtil;
import com.zhao.myreader.util.StringHelper;
import com.zhao.myreader.util.TextHelper;
import com.zhao.myreader.webapi.CommonApi;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by zhao on 2017/7/27.
 */

public class ReadPresenter implements BasePresenter {

    private ReadActivity mReadActivity;
    private Book mBook;
    private ArrayList<Chapter> mChapters = new ArrayList<>();
    private ArrayList<Chapter> mInvertedOrderChapters = new ArrayList<>();
    private ChapterService mChapterService;
    private BookService mBookService;
    private ChapterContentAdapter mChapterContentAdapter;
    private ChapterTitleAdapter mChapterTitleAdapter;
    private Setting mSetting;

    private boolean settingChange;//是否是设置改变

    private float pointX;
    private float pointY;

    private float settingOnClickValidFrom;
    private float settingOnClickValidTo;


    private Dialog mDialog;

    private int curSortflag = 0; //0正序  1倒序


    private int num = -1;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    init();
                    break;
                case 2:
                    mReadActivity.getPbLoading().setVisibility(View.GONE);
                    mReadActivity.getSrlContent().finishLoadmore();
                    break;
                case 3:
                    mReadActivity.getLvContent().setSelection(msg.arg1);
                    mReadActivity.getPbLoading().setVisibility(View.GONE);
                    break;
            }
        }
    };


    public ReadPresenter(ReadActivity readActivity) {
        mReadActivity = readActivity;
        mBookService = new BookService();
        mChapterService = new ChapterService();
        mSetting = SysManager.getSetting();
    }


    @Override
    public void start() {
        if (mSetting.isDayStyle()) {
            mReadActivity.getDlReadActivity().setBackgroundResource(mSetting.getReadBgColor());
        } else {
            mReadActivity.getDlReadActivity().setBackgroundResource(R.color.sys_night_bg);
        }
        if (!mSetting.isBrightFollowSystem()){
            BrightUtil.setBrightness(mReadActivity,mSetting.getBrightProgress());
        }
        mBook = (Book) mReadActivity.getIntent().getSerializableExtra(APPCONST.BOOK);
        settingOnClickValidFrom = BaseActivity.height / 4;
        settingOnClickValidTo = BaseActivity.height / 4 * 3;
        mReadActivity.getSrlContent().setEnableAutoLoadmore(true);
        mReadActivity.getSrlContent().setEnableRefresh(false);
        mReadActivity.getSrlContent().setOnLoadmoreListener(new OnLoadmoreListener() {
            @Override
            public void onLoadmore(RefreshLayout refreshlayout) {
                settingChange = true;
                getData();
            }
        });
        mReadActivity.getPbLoading().setVisibility(View.VISIBLE);
        mReadActivity.getLvContent().setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                pointX = motionEvent.getX();
                pointY = motionEvent.getY();
                return false;
            }
        });
        mReadActivity.getLvContent().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (pointY > settingOnClickValidFrom && pointY < settingOnClickValidTo) {
                    int progress = mReadActivity.getLvContent().getLastVisiblePosition() * 100 / (mChapters.size() - 1);
                    mDialog = DialogCreator.createReadSetting(mReadActivity, mSetting.isDayStyle(), progress, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {//返回
                            mReadActivity.finish();
                        }
                    }, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {//上一章
                            int curPosition = mReadActivity.getLvContent().getLastVisiblePosition();
                            if (curPosition > 0) {
                                mBook.setHisttoryChapterNum(curPosition - 1);
                                mReadActivity.getLvContent().setSelection(curPosition - 1);
                            }
                        }
                    }, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {//下一章
                            int curPosition = mReadActivity.getLvContent().getLastVisiblePosition();
                            if (curPosition < mChapters.size() - 1) {
                                mBook.setHisttoryChapterNum(curPosition + 1);
                                mReadActivity.getLvContent().setSelection(curPosition + 1);
                            }
                        }
                    }, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {//目录
                            initChapterTitleList();
                            mReadActivity.getDlReadActivity().openDrawer(GravityCompat.START);
                            mDialog.dismiss();

                        }
                    }, new DialogCreator.OnClickNightAndDayListener() {
                        @Override
                        public void onClick(Dialog dialog, View view, boolean isDayStyle) {//日夜切换
                            changeNightAndDaySetting(isDayStyle);


                        }
                    }, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {//设置
                            mDialog.dismiss();
                            DialogCreator.createReadDetailSetting(mReadActivity, mSetting,
                                    new DialogCreator.OnReadStyleChangeListener() {
                                        @Override
                                        public void onChange(ReadStyle readStyle) {
                                            changeStyle(readStyle);
                                        }
                                    }, new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            reduceTextSize();
                                        }
                                    }, new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            increaseTextSize();
                                        }
                                    }, new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            if (mSetting.getLanguage() == Language.simplified){
                                                mSetting.setLanguage(Language.traditional);
                                            }else {
                                                mSetting.setLanguage(Language.simplified);
                                            }
                                            SysManager.saveSetting(mSetting);
                                            settingChange = true;
                                            init();
                                        }
                                    });

                        }
                    }, new SeekBar.OnSeekBarChangeListener() {//阅读进度
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                            mReadActivity.getPbLoading().setVisibility(View.VISIBLE);
                            final int chapterNum = (mChapters.size() - 1) * i / 100;
                            mBook.setHisttoryChapterNum(chapterNum);
                            getChapterContent(mChapters.get(chapterNum), new ResultCallback() {
                                @Override
                                public void onFinish(Object o, int code) {
                                    mChapters.get(chapterNum).setContent((String) o);
                                    mChapterService.saveOrUpdateChapter(mChapters.get(chapterNum));
                                    mHandler.sendMessage(mHandler.obtainMessage(1));
                                }

                                @Override
                                public void onError(Exception e) {
                                    mHandler.sendMessage(mHandler.obtainMessage(1));
                                }
                            });

                        }

                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {

                        }

                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {

                        }
                    });

                } else if (pointY > settingOnClickValidTo) {

                    mReadActivity.getLvContent().smoothScrollBy(BaseActivity.height, 200);
                } else if (pointX > settingOnClickValidFrom) {

                    mReadActivity.getLvContent().smoothScrollBy(-BaseActivity.height, 200);
                }
            }
        });
        mReadActivity.getLvChapterList(). setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, final int i, long l) {
                        //关闭侧滑菜单
                        mReadActivity.getDlReadActivity().closeDrawer(GravityCompat.START);
                       final int position;
                        if (curSortflag == 0){
                            position = i;
                        }else {
                            position = mChapters.size() - 1 - i;
                        }
                        mBook.setHisttoryChapterNum(position);

                        if (StringHelper.isEmpty(mChapters.get(position).getContent())) {
                            mReadActivity.getPbLoading().setVisibility(View.VISIBLE);
                            CommonApi.getChapterContent(mChapters.get(position).getUrl(), new ResultCallback() {
                                @Override
                                public void onFinish(Object o, int code) {
                                    mChapters.get(position).setContent((String) o);
                                    mHandler.sendMessage(mHandler.obtainMessage(3, position, 0));
                                }

                                @Override
                                public void onError(Exception e) {

                                }
                            });
                        } else {
                            mReadActivity.getLvContent().setSelection(position);
                        }

                    }
                });

        mReadActivity.getTvChapterSort().setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (curSortflag == 0) {//当前正序
                            mReadActivity.getTvChapterSort().setText(mReadActivity.getString(R.string.positive_sort));
                            curSortflag = 1;
                            changeChapterSort();
                        } else {//当前倒序
                            mReadActivity.getTvChapterSort().setText(mReadActivity.getString(R.string.inverted_sort));
                            curSortflag = 0;
                            changeChapterSort();
                        }
                    }
                });

        mChapters = (ArrayList<Chapter>) mChapterService.findBookAllChapterByBookId(mBook.getId());
        setDrawerLeftEdgeSize(mReadActivity,mReadActivity.getDlReadActivity(),0);
        getData();
    }

    /**
     * 缩小字体
     */
    private void reduceTextSize(){
       if (mSetting.getReadWordSize() > 1){
           mSetting.setReadWordSize(mSetting.getReadWordSize() - 1);
           SysManager.saveSetting(mSetting);
           settingChange = true;
           initContent();
       }
    }

    /**
     * 增大字体
     */
    private void increaseTextSize(){
        if (mSetting.getReadWordSize() < 30){
            mSetting.setReadWordSize(mSetting.getReadWordSize() + 1);
            SysManager.saveSetting(mSetting);
            settingChange = true;
            initContent();
        }
    }

    /**
     * 改变阅读风格
     *
     * @param readStyle
     */
    private void changeStyle(ReadStyle readStyle) {
        settingChange = true;
        if (!mSetting.isDayStyle()) mSetting.setDayStyle(true);
        mSetting.setReadStyle(readStyle);
        switch (readStyle) {
            case common:
                mSetting.setReadBgColor(R.color.sys_common_bg);
                mSetting.setReadWordColor(R.color.sys_common_word);
                break;
            case leather:
                mSetting.setReadBgColor(R.mipmap.theme_leather_bg);
                mSetting.setReadWordColor(R.color.sys_leather_word);
                break;
            case protectedEye:
                mSetting.setReadBgColor(R.color.sys_protect_eye_bg);
                mSetting.setReadWordColor(R.color.sys_protect_eye_word);
                break;
            case breen:
                mSetting.setReadBgColor(R.color.sys_breen_bg);
                mSetting.setReadWordColor(R.color.sys_breen_word);
                break;
            case blueDeep:
                mSetting.setReadBgColor(R.color.sys_blue_deep_bg);
                mSetting.setReadWordColor(R.color.sys_blue_deep_word);
                break;
        }
        SysManager.saveSetting(mSetting);
        init();
    }

    private void init() {
        initContent();
        initChapterTitleList();
    }

    /**
     * 初始化主内容视图
     */
    private void initContent() {
        if (mSetting.isDayStyle()) {
            mReadActivity.getDlReadActivity().setBackgroundResource(mSetting.getReadBgColor());
        } else {
            mReadActivity.getDlReadActivity().setBackgroundResource(R.color.sys_night_bg);
        }
        if (mChapterContentAdapter == null) {
            mChapterContentAdapter = new ChapterContentAdapter(mReadActivity, R.layout.listview_chapter_content_item, mChapters);
            mReadActivity.getLvContent().setAdapter(mChapterContentAdapter);
        } else {
            mChapterContentAdapter.notifyDataSetChanged();
        }
        if (!settingChange) {
            mReadActivity.getLvContent().setSelection(mBook.getHisttoryChapterNum());
        } else {
            settingChange = false;
        }
        mReadActivity.getPbLoading().setVisibility(View.GONE);
        mReadActivity.getSrlContent().finishLoadmore();
    }

    private void changeChapterSort(){
        //设置布局管理器
        if (curSortflag == 0) {
            mChapterTitleAdapter = new ChapterTitleAdapter(mReadActivity, R.layout.listview_chapter_title_item, mChapters);
        } else {
            mChapterTitleAdapter = new ChapterTitleAdapter(mReadActivity, R.layout.listview_chapter_title_item, mInvertedOrderChapters);
        }
        mReadActivity.getLvChapterList().setAdapter(mChapterTitleAdapter);

    }

    /**
     * 初始化章节目录视图
     */
    private void initChapterTitleList() {
        if (mSetting.isDayStyle()) {
            mReadActivity.getTvBookList().setTextColor(mReadActivity.getResources().getColor(mSetting.getReadWordColor()));
            mReadActivity.getTvChapterSort().setTextColor(mReadActivity.getResources().getColor(mSetting.getReadWordColor()));
        }else {
            mReadActivity.getTvBookList().setTextColor(mReadActivity.getResources().getColor(R.color.sys_night_word));
            mReadActivity.getTvChapterSort().setTextColor(mReadActivity.getResources().getColor(R.color.sys_night_word));
        }
        if (mSetting.isDayStyle()) {
            mReadActivity.getLlChapterListView().setBackgroundResource(mSetting.getReadBgColor());
        } else {
            mReadActivity.getLlChapterListView().setBackgroundResource(R.color.sys_night_bg);
        }
        int selectedPostion,curChapterPosition;

        //设置布局管理器
        if (curSortflag == 0) {
            mChapterTitleAdapter = new ChapterTitleAdapter(mReadActivity, R.layout.listview_chapter_title_item, mChapters);
            curChapterPosition = mReadActivity.getLvContent().getLastVisiblePosition();
            selectedPostion = curChapterPosition - 5;
            if (selectedPostion < 0)  selectedPostion = 0;
            if (mChapters.size() - 1 - curChapterPosition < 5) selectedPostion = mChapters.size();
            mChapterTitleAdapter.setCurChapterPosition(curChapterPosition);
        } else {
            mChapterTitleAdapter = new ChapterTitleAdapter(mReadActivity, R.layout.listview_chapter_title_item, mInvertedOrderChapters);
            curChapterPosition = mChapters.size() - 1 - mReadActivity.getLvContent().getLastVisiblePosition();
            selectedPostion = curChapterPosition - 5;
            if (selectedPostion < 0)  selectedPostion = 0;
            if (mChapters.size() - 1 - curChapterPosition < 5) selectedPostion = mChapters.size();
            mChapterTitleAdapter.setCurChapterPosition(curChapterPosition);
        }
        mReadActivity.getLvChapterList().setAdapter(mChapterTitleAdapter);
        mReadActivity.getLvChapterList().setSelection(selectedPostion);

    }


    /**
     * 章节数据网络同步
     */
    private void getData() {

        CommonApi.getBookChapters(mBook.getChapterUrl(), new ResultCallback() {
            @Override
            public void onFinish(Object o, int code) {
                final ArrayList<Chapter> chapters = (ArrayList<Chapter>) o;
                if (mChapters.size() < chapters.size()) {
                    mChapters.addAll(chapters.subList(mChapters.size(), chapters.size()));
                } else {
                    settingChange = false;
                }
                mInvertedOrderChapters.clear();
                mInvertedOrderChapters.addAll(mChapters);
                Collections.reverse(mInvertedOrderChapters);
                if (mChapters.size() == 0) {
                    TextHelper.showLongText("该书查询不到任何章节");
                    mReadActivity.getPbLoading().setVisibility(View.GONE);
                    settingChange = false;
                } else {
                    if (mBook.getHisttoryChapterNum() < 0) mBook.setHisttoryChapterNum(0);
                    getChapterContent(chapters.get(mBook.getHisttoryChapterNum()), new ResultCallback() {
                        @Override
                        public void onFinish(Object o, int code) {
                            mChapters.get(mBook.getHisttoryChapterNum()).setContent((String) o);
                            mChapterService.saveOrUpdateChapter(mChapters.get(mBook.getHisttoryChapterNum()));
                            mHandler.sendMessage(mHandler.obtainMessage(1));
//                        getAllChapterData();
                        }

                        @Override
                        public void onError(Exception e) {
                            mHandler.sendMessage(mHandler.obtainMessage(1));

                        }
                    });
                    if (!StringHelper.isEmpty(mBook.getId())) {
                        for (Chapter chapter : mChapters) {
                            chapter.setBookId(mBook.getId());
                            if (StringHelper.isEmpty(chapter.getId())) {
                                mChapterService.addChapter(chapter);
                            }
                        }
                    }
                }

            }

            @Override
            public void onError(Exception e) {
                mHandler.sendMessage(mHandler.obtainMessage(2));
                settingChange = false;
            }
        });
    }

    /**
     * 缓存所有章节
     */
    private void getAllChapterData() {
        MyApplication.getApplication().newThread(new Runnable() {
            @Override
            public void run() {
                for (final Chapter chapter : mChapters) {
                    getChapterContent(chapter, null);

                }
            }
        });

    }

    private void getChapterContent(final Chapter chapter, ResultCallback resultCallback) {

        if (!StringHelper.isEmpty(chapter.getContent())) {
            if (resultCallback != null) {
                resultCallback.onFinish(chapter.getContent(), 0);
            }
        } else {
            if (resultCallback != null) {
                CommonApi.getChapterContent(chapter.getUrl(), resultCallback);
            } else {
                CommonApi.getChapterContent(chapter.getUrl(), new ResultCallback() {
                    @Override
                    public void onFinish(final Object o, int code) {
                        chapter.setContent((String) o);
                        mChapterService.saveOrUpdateChapter(chapter);
                    }

                    @Override
                    public void onError(Exception e) {

                    }

                });
            }

        }
    }


    /**
     * 白天夜间改变
     *
     * @param isCurDayStyle
     */
    private void changeNightAndDaySetting(boolean isCurDayStyle) {

        mSetting.setDayStyle(!isCurDayStyle);
        SysManager.saveSetting(mSetting);
        settingChange = true;
        init();
    }


    public void saveHistory() {
        if (!StringHelper.isEmpty(mBook.getId())) {
            mBook.setHisttoryChapterNum(mReadActivity.getLvContent().getLastVisiblePosition());
            mBookService.updateEntity(mBook);
        }
    }

    private void setThemeColor(int colorPrimary, int colorPrimaryDark) {
//        mToolbar.setBackgroundResource(colorPrimary);
        mReadActivity.getSrlContent().setPrimaryColorsId(colorPrimary, android.R.color.white);
        if (Build.VERSION.SDK_INT >= 21) {
            mReadActivity.getWindow().setStatusBarColor(ContextCompat.getColor(mReadActivity, colorPrimaryDark));
        }
    }

    /**
     * 设置DrawerLayout滑动范围
     *
     * @param activity
     * @param drawerLayout
     * @param displayWidthPercentage 1 全屏滑动
     */
    private void setDrawerLeftEdgeSize(Activity activity, DrawerLayout drawerLayout, float displayWidthPercentage) {
        if (activity == null || drawerLayout == null) return;
        try {
            // 找到 ViewDragHelper 并设置 Accessible 为true
            Field leftDraggerField =
                    drawerLayout.getClass().getDeclaredField("mLeftDragger");//Right
            leftDraggerField.setAccessible(true);
            ViewDragHelper leftDragger = (ViewDragHelper) leftDraggerField.get(drawerLayout);

            // 找到 edgeSizeField 并设置 Accessible 为true
            Field edgeSizeField = leftDragger.getClass().getDeclaredField("mEdgeSize");
            edgeSizeField.setAccessible(true);
            int edgeSize = edgeSizeField.getInt(leftDragger);

            // 设置新的边缘大小
            Point displaySize = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
            edgeSizeField.setInt(leftDragger, Math.max(edgeSize, (int) (displaySize.x *
                    displayWidthPercentage)));
        } catch (NoSuchFieldException e) {
        } catch (IllegalArgumentException e) {
        } catch (IllegalAccessException e) {
        }
    }


}
