package com.justwayward.reader.view.ReadView;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.v4.content.ContextCompat;

import com.justwayward.reader.R;
import com.justwayward.reader.bean.BookToc;
import com.justwayward.reader.utils.AppUtils;
import com.justwayward.reader.utils.FileUtils;
import com.justwayward.reader.utils.ScreenUtils;
import com.justwayward.reader.utils.SharedPreferencesUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.DecimalFormat;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Vector;

public class PageFactory {
    /**
     * 屏幕宽高
     */
    private int mHeight, mWidth;
    /**
     * 文字区域宽高
     */
    private int mVisibleHeight, mVisibleWidth;
    /**
     * 间距
     */
    private int marginHeight, marginWidth;
    /**
     * 字体大小
     */
    private int mFontSize, mNumFontSize;

    /**
     * 每页行数
     */
    private int mPageLineCount;
    /**
     * 行间距
     **/
    private int mLineSpace;
    /**
     * 字节长度
     */
    private int m_mpBufferLen;
    /**
     * MappedByteBuffer：高效的文件内存映射
     */
    private MappedByteBuffer m_mpBuff;
    // 页首页尾的位置
    private int m_mbBufEndPos = 0;
    private int m_mbBufBeginPos = 0;
    private Paint mPaint;
    private Paint mTitlePaint;

    private Bitmap m_book_bg;
    private Vector<String> m_lines = new Vector<>();

    private String basePath = FileUtils.createRootPath(AppUtils.getAppContext()) + "/book/";
    private String bookId;
    private int currentChapter;
    private List<BookToc.mixToc.Chapters> chaptersList;
    private int chapterSize = 0;
    private int currentPage = 1;

    private OnReadStateChangeListener listener;

    public PageFactory(String bookId, int chapter, List<BookToc.mixToc.Chapters> chaptersList) {
        this(ScreenUtils.getScreenWidth(), ScreenUtils.getScreenHeight(), ScreenUtils.dpToPxInt(18),
                bookId, chapter, chaptersList);
    }

    public PageFactory(int width, int height, int fontSize, String bookId, int chapter,
                       List<BookToc.mixToc.Chapters> chaptersList) {
        mWidth = width;
        mHeight = height;
        mFontSize = fontSize;
        mNumFontSize = fontSize;
        marginWidth = ScreenUtils.dpToPxInt(15);
        marginHeight = ScreenUtils.dpToPxInt(15);
        mVisibleHeight = mHeight - marginHeight * 2 - mFontSize * 3;
        mVisibleWidth = mWidth - marginWidth * 2;
        mLineSpace = mFontSize / 3;
        mPageLineCount = mVisibleHeight / (mFontSize + mLineSpace);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setTextSize(mFontSize);
        mPaint.setColor(Color.BLACK);
        mTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTitlePaint.setTextSize(mNumFontSize);
        mTitlePaint.setColor(ContextCompat.getColor(AppUtils.getAppContext(), R.color.light_coffee));
        // Typeface typeface = Typeface.createFromAsset(context.getAssets(),"fonts/FZBYSK.TTF");
        // mPaint.setTypeface(typeface);
        // mNumPaint.setTypeface(typeface);

        this.bookId = bookId;
        this.currentChapter = chapter;
        this.chaptersList = chaptersList;
        chapterSize = chaptersList.size();
    }

    public File getBookFile(int chapter) {
        File file = new File(basePath + bookId + "/" + chapter + ".txt");
        if (!file.exists())
            FileUtils.createFile(file);
        return file;
    }

    public void openBook() {
        openBook(new int[]{0, 0});
    }

    public void openBook(int[] position) {
        openBook(1, position);
    }

    /**
     * 打开书籍文件
     *
     * @param chapter  阅读章节
     * @param position 阅读位置
     * @return 0：文件不存在或打开失败  1：打开成功
     */
    public int openBook(int chapter, int[] position) {
        this.currentChapter = chapter;
        String path = getBookFile(currentChapter).getPath();
        try {
            File file = new File(path);
            long length = file.length();
            if (length < 50) {
                return 0;
            }
            m_mpBufferLen = (int) length;
            // 创建文件通道，映射为MappedByteBuffer
            m_mpBuff = new RandomAccessFile(file, "r")
                    .getChannel()
                    .map(FileChannel.MapMode.READ_ONLY, 0, length);
            m_mbBufBeginPos = position[0];
            m_mbBufEndPos = position[1];
            onChapterChanged(chapter);
            m_lines.clear();
            return 1;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * 绘制阅读页面
     *
     * @param canvas
     */
    public void onDraw(Canvas canvas) {
        if (m_lines.size() == 0) {
            m_mbBufEndPos = m_mbBufBeginPos;
            m_lines = pageDown();
        }
        if (m_lines.size() > 0) {
            int y = marginHeight + (mLineSpace << 1);
            // 绘制背景
            if (m_book_bg != null) {
                Rect rectF = new Rect(0, 0, mWidth, mHeight);
                canvas.drawBitmap(m_book_bg, null, rectF, null);
            } else {
                canvas.drawColor(Color.WHITE);
            }
            // 绘制标题
            canvas.drawText(chaptersList.get(currentChapter - 1).title, marginWidth, y, mTitlePaint);
            y += mLineSpace << 1;
            // 绘制阅读页面文字
            for (String line : m_lines) {
                y += mFontSize + mLineSpace;
                if (line.endsWith("@")) {
                    canvas.drawText(line.substring(0, line.length() - 1), marginWidth, y, mPaint);
                    y += mLineSpace;
                    canvas.drawText(" ", marginWidth, y, mPaint);
                } else {
                    canvas.drawText(line, marginWidth, y, mPaint);
                }
            }
            // 绘制提示内容
            float percent = (float) currentChapter * 100 / chapterSize;
            DecimalFormat format = new DecimalFormat("#0.00");
            canvas.drawText(format.format(percent) + "%", marginWidth + 2, mHeight - marginHeight, mTitlePaint);
            GregorianCalendar calendar = new GregorianCalendar();
            String mTime = calendar.HOUR_OF_DAY + ":" + calendar.MINUTE;
            int strLen = (int) mTitlePaint.measureText(mTime);
            canvas.drawText(mTime, mWidth - marginWidth - strLen, mHeight - marginHeight, mTitlePaint);
        }
    }

    /**
     * 指针移到上一页页首
     */
    private void pageUp() {
        String strParagraph = "";
        Vector<String> lines = new Vector<>(); // 页面行
        int paraSpace = 0;
        mPageLineCount = mVisibleHeight / (mFontSize + mLineSpace);
        while ((lines.size() < mPageLineCount) && (m_mbBufBeginPos > 0)) {
            Vector<String> paraLines = new Vector<>(); // 段落行
            byte[] parabuffer = readParagraphBack(m_mbBufBeginPos); // 1.读取上一个段落

            m_mbBufBeginPos -= parabuffer.length; // 2.变换起始位置指针
            try {
                strParagraph = new String(parabuffer, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            strParagraph = strParagraph.replaceAll("\r\n", "  ");
            strParagraph = strParagraph.replaceAll("\n", " ");

            while (strParagraph.length() > 0) { // 3.逐行添加到lines
                int paintSize = mPaint.breakText(strParagraph, true, mVisibleWidth, null);
                paraLines.add(strParagraph.substring(0, paintSize));
                strParagraph = strParagraph.substring(paintSize);
            }
            lines.addAll(0, paraLines);

            while (lines.size() > mPageLineCount) { // 4.如果段落添加完，但是超出一页，则超出部分需删减
                try {
                    m_mbBufBeginPos += lines.get(0).getBytes("UTF-8").length; // 5.删减行数同时起始位置指针也要跟着偏移
                    lines.remove(0);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            m_mbBufEndPos = m_mbBufBeginPos; // 6.最后结束指针指向下一段的开始处
            paraSpace += mLineSpace;
            mPageLineCount = (mVisibleHeight - paraSpace) / (mFontSize + mLineSpace); // 添加段落间距，实时更新行数
        }
    }

    /**
     * 根据起始位置指针，读取一页内容
     *
     * @return
     */
    private Vector<String> pageDown() {
        String strParagraph = "";
        Vector<String> lines = new Vector<>();
        int paraSpace = 0;
        mPageLineCount = mVisibleHeight / (mFontSize + mLineSpace);
        while ((lines.size() < mPageLineCount) && (m_mbBufEndPos < m_mpBufferLen)) {
            byte[] parabuffer = readParagraphForward(m_mbBufEndPos);
            m_mbBufEndPos += parabuffer.length;
            try {
                strParagraph = new String(parabuffer, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            strParagraph = strParagraph.replaceAll("\r\n", "  ");
            strParagraph = strParagraph.replaceAll("\n", " "); // 段落中的换行符去掉，绘制的时候再换行

            while (strParagraph.length() > 0) {
                int paintSize = mPaint.breakText(strParagraph, true, mVisibleWidth, null);
                lines.add(strParagraph.substring(0, paintSize));
                strParagraph = strParagraph.substring(paintSize);
                if (lines.size() >= mPageLineCount) {
                    break;
                }
            }
            lines.set(lines.size() - 1, lines.get(lines.size() - 1) + "@");
            if (strParagraph.length() != 0) {
                try {
                    m_mbBufEndPos -= (strParagraph).getBytes("UTF-8").length;
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            paraSpace += mLineSpace;
            mPageLineCount = (mVisibleHeight - paraSpace) / (mFontSize + mLineSpace);
        }
        SharedPreferencesUtil.getInstance().putInt(bookId + "-chapter", currentChapter);
        SharedPreferencesUtil.getInstance().putInt(bookId + "-startPos", m_mbBufBeginPos);
        SharedPreferencesUtil.getInstance().putInt(bookId + "-endPos", m_mbBufEndPos);
        return lines;
    }

    /**
     * 获取最后一页的内容。比较繁琐，待优化
     *
     * @return
     */
    public Vector<String> pageLast() {
        String strParagraph = "";
        Vector<String> lines = new Vector<String>();
        currentPage = 0;
        while (m_mbBufEndPos < m_mpBufferLen) {
            int paraSpace = 0;
            mPageLineCount = mVisibleHeight / (mFontSize + mLineSpace);
            m_mbBufBeginPos = m_mbBufEndPos;
            while ((lines.size() < mPageLineCount) && (m_mbBufEndPos < m_mpBufferLen)) {
                byte[] parabuffer = readParagraphForward(m_mbBufEndPos);
                m_mbBufEndPos += parabuffer.length;
                try {
                    strParagraph = new String(parabuffer, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                strParagraph = strParagraph.replaceAll("\r\n", "  ");
                strParagraph = strParagraph.replaceAll("\n", " "); // 段落中的换行符去掉，绘制的时候再换行

                while (strParagraph.length() > 0) {
                    int paintSize = mPaint.breakText(strParagraph, true, mVisibleWidth, null);
                    lines.add(strParagraph.substring(0, paintSize));
                    strParagraph = strParagraph.substring(paintSize);
                    if (lines.size() >= mPageLineCount) {
                        break;
                    }
                }
                lines.set(lines.size() - 1, lines.get(lines.size() - 1) + "@");

                if (strParagraph.length() != 0) {
                    try {
                        m_mbBufEndPos -= (strParagraph).getBytes("UTF-8").length;
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
                paraSpace += mLineSpace;
                mPageLineCount = (mVisibleHeight - paraSpace) / (mFontSize + mLineSpace);
            }
            if (m_mbBufEndPos < m_mpBufferLen) {
                lines.clear();
            }
            currentPage++;
        }
        SharedPreferencesUtil.getInstance().putInt(bookId + "-chapter", currentChapter);
        SharedPreferencesUtil.getInstance().putInt(bookId + "-startPos", m_mbBufBeginPos);
        SharedPreferencesUtil.getInstance().putInt(bookId + "-endPos", m_mbBufEndPos);
        return lines;
    }

    /**
     * 读取下一段落
     *
     * @param m_mbBufEndPos 当前页结束位置指针
     * @return
     */
    private byte[] readParagraphForward(int m_mbBufEndPos) {
        byte b0;
        int i = m_mbBufEndPos;
        while (i < m_mpBufferLen) {
            b0 = m_mpBuff.get(i++);
            if (b0 == 0x0a) {
                break;
            }
        }
        int nParaSize = i - m_mbBufEndPos;
        byte[] buf = new byte[nParaSize];
        for (i = 0; i < nParaSize; i++) {
            buf[i] = m_mpBuff.get(m_mbBufEndPos + i);
        }
        return buf;
    }

    /**
     * 读取上一段落
     *
     * @param m_mbBufBeginPos 当前页起始位置指针
     * @return
     */
    private byte[] readParagraphBack(int m_mbBufBeginPos) {
        byte b0;
        int i = m_mbBufBeginPos - 1;
        while (i > 0) {
            b0 = m_mpBuff.get(i);
            if (b0 == 0x0a && i != m_mbBufBeginPos - 1) {
                i++;
                break;
            }
            i--;
        }
        int nParaSize = m_mbBufBeginPos - i;
        byte[] buf = new byte[nParaSize];
        for (int j = 0; j < nParaSize; j++) {
            buf[j] = m_mpBuff.get(i + j);
        }
        return buf;
    }

    public boolean hasNextPage() {
        return currentChapter < chaptersList.size() || m_mbBufEndPos < m_mpBufferLen;
    }

    public boolean hasPrePage() {
        return currentChapter > 1 || (currentChapter == 1 && m_mbBufBeginPos > 0);
    }

    /**
     * 跳转下一页
     */
    public boolean nextPage() {
        if (!hasNextPage()) { // 最后一章的结束页
            return false;
        } else {
            if (m_mbBufEndPos >= m_mpBufferLen) { // 中间章节结束页
                currentChapter++;
                int ret = openBook(currentChapter, new int[]{0, 0}); // 打开下一章
                if (ret == 0) {
                    onLoadChapterFailure(currentChapter);
                    return false;
                } else {
                    currentPage = 0;
                    onChapterChanged(currentChapter);
                }
            }
            m_lines.clear();
            m_mbBufBeginPos = m_mbBufEndPos; // 起始指针移到结束位置
            m_lines = pageDown(); // 读取一页内容
            onPageChanged(currentChapter, ++currentPage);
        }
        return true;
    }

    /**
     * 跳转上一页
     */
    public boolean prePage() {
        if (!hasPrePage()) { // 第一章第一页
            return false;
        } else {
            if (m_mbBufBeginPos <= 0) {
                currentChapter--;
                int ret = openBook(currentChapter, new int[]{0, 0});
                if (ret == 0) {
                    onLoadChapterFailure(currentChapter);
                    return false;
                } else { // 跳转到上一章的最后一页
                    m_lines.clear();
                    m_lines = pageLast();
                    onChapterChanged(currentChapter);
                    onPageChanged(currentChapter, currentPage);
                    return true;
                }
            }
            m_lines.clear();
            pageUp(); // 起始指针移到上一页开始处
            m_lines = pageDown(); // 读取一页内容
            onPageChanged(currentChapter, --currentPage);
        }
        return true;
    }

    /**
     * 获取当前阅读位置
     *
     * @return index 0：起始位置 1：结束位置
     */
    public int[] getPosition() {
        return new int[]{m_mbBufBeginPos, m_mbBufEndPos};
    }

    /**
     * 设置字体大小
     *
     * @param fontsize 单位：px
     */
    public void setTextFont(int fontsize) {
        mFontSize = fontsize;
        mPaint.setTextSize(mFontSize);
        mPageLineCount = mVisibleHeight / (mFontSize + mLineSpace);
        m_mbBufEndPos = m_mbBufBeginPos;
        nextPage();
    }

    public int getTextFont() {
        return mFontSize;
    }

    /**
     * 根据百分比，跳到目标位置
     *
     * @param persent
     */
    public void setPercent(int persent) {
        float a = (float) (m_mpBufferLen * persent) / 100;
        m_mbBufEndPos = (int) a;
        if (m_mbBufEndPos == 0) {
            nextPage();
        } else {
            nextPage();
            prePage();
            nextPage();
        }
    }

    public void setBgBitmap(Bitmap BG) {
        m_book_bg = BG;
    }

    public void setOnReadStateChangeListener(OnReadStateChangeListener listener) {
        this.listener = listener;
    }

    void onChapterChanged(int chapter) {
        if (listener != null)
            listener.onChapterChanged(chapter);
    }

    void onPageChanged(int chapter, int page) {
        if (listener != null)
            listener.onPageChanged(chapter, page);
    }

    void onLoadChapterFailure(int chapter) {
        if (listener != null)
            listener.onLoadChapterFailure(chapter);
    }
}
