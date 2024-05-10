package com.hmdp.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

//滚动分页返回的结果
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
