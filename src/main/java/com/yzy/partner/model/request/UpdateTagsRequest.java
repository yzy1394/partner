package com.yzy.partner.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户更新标签
 *
 * @author yzy
 */
@Data
public class UpdateTagsRequest implements Serializable {
    /**
     * 所更改用户的id
     */
    private long id;

    /**
     * 旧标签
     */
    private String oldTag;

    /**
     * 新标签
     */
    private String newTag;

    /**
     * 指定更新操作（删除，增加，更新）
     */
    private String operation;
}
