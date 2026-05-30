package com.qingledger.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "修改用户资料请求")
public class UpdateProfileRequest {

    @Schema(description = "昵称(1-20字符)，null则不更新")
    @Size(min = 1, max = 20, message = "昵称长度必须在1-20个字符之间")
    private String nickname;

    @Schema(description = "头像URL(最大255字符)，null则不更新，空字符串则清空")
    @Size(max = 255, message = "头像URL长度不能超过255个字符")
    private String avatar;

    public void setNickname(String nickname) {
        this.nickname = nickname != null ? nickname.trim() : null;
    }
}
