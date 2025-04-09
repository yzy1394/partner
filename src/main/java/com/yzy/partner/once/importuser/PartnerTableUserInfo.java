package com.yzy.partner.once.importuser;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 表格用户信息
 */
@Data
public class PartnerTableUserInfo {

    /**
     * id
     */
    @ExcelProperty("成员编号")
    private String code;

    /**
     * 用户昵称
     */
    @ExcelProperty("成员昵称")
    private String username;


}