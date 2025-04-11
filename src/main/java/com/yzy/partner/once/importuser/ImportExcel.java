package com.yzy.partner.once.importuser;

import com.alibaba.excel.EasyExcel;

import java.util.List;

/**
 * 导入 Excel
 *
 * @author yzy
 */
public class ImportExcel {

    /**
     * 读取数据
     */
    public static void main(String[] args) {
        String fileName = "E:\\IDEA_workspace\\partner\\src\\main\\resources\\testExcel.xlsx";
//        readByListener(fileName);
        synchronousRead(fileName);
    }

    /**
     * 监听器读取
     *
     * @param fileName
     */
    public static void readByListener(String fileName) {
        EasyExcel.read(fileName, PartnerTableUserInfo.class, new TableListener()).sheet().doRead();
    }


    /**
     * 同步读
     *
     * @param fileName
     */
    public static void synchronousRead(String fileName) {
        // 这里 需要指定读用哪个class去读，然后读取第一个sheet 同步读取会自动finish
        List<PartnerTableUserInfo> totalDataList =
                EasyExcel.read(fileName).head(PartnerTableUserInfo.class).sheet().doReadSync();
        for (PartnerTableUserInfo partnerTableUserInfo : totalDataList) {
            System.out.println(partnerTableUserInfo);
        }
    }

}
