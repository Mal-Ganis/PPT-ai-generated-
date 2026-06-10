package com.example.pptbackend.dto;

import java.util.ArrayList;
import java.util.List;

public class RegenerateSlideFromSourcesRequest {

    /** 可选；不传则使用库中当前标题 */
    private String title;

    /** 用户已确认的本页引用（必填） */
    private List<String> sources = new ArrayList<>();

    /** 全稿主题/输入内容，用于保持叙事一致 */
    private String inputContent;

    /** 重生前的讲稿要点（可选），供模型把握改写方向 */
    private List<String> previousContent = new ArrayList<>();

    public String getInputContent() {
        return inputContent;
    }

    public void setInputContent(String inputContent) {
        this.inputContent = inputContent;
    }

    public List<String> getPreviousContent() {
        return previousContent;
    }

    public void setPreviousContent(List<String> previousContent) {
        this.previousContent = previousContent != null ? previousContent : new ArrayList<>();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources != null ? sources : new ArrayList<>();
    }
}
