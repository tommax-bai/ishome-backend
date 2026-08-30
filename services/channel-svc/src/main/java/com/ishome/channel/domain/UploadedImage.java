package com.ishome.channel.domain;

/**
 * 一张已经落进私有桶的用户上传图：键 + 它自己说的格式。
 *
 * <p>渠道侧对这张图只知道这两件事——**它是不是户型图、画的是几室几厅，渠道层一概不理解**（语义归会话侧与解析）。
 */
public record UploadedImage(String objectKey, String mimeType) {}
