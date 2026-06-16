package com.qingledger.controller;

import com.qingledger.common.BusinessException;
import com.qingledger.common.Result;
import com.qingledger.dto.request.*;
import com.qingledger.entity.InvitationCode;
import com.qingledger.entity.Ledger;
import com.qingledger.entity.LedgerMember;
import com.qingledger.entity.User;
import com.qingledger.service.ledger.LedgerService;
import com.qingledger.utils.UserContext;
import com.qingledger.vo.LedgerResponse;
import com.qingledger.vo.MemberResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "账本接口", description = "账本管理")
@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @Operation(summary = "创建账本", description = "创建个人或共享账本",
               security = @SecurityRequirement(name = "JWT"))
    @PostMapping
    public Result<LedgerResponse> createLedger(@Valid @RequestBody CreateLedgerRequest req) {
        Long userId = getCurrentUserId();
        Ledger created = ledgerService.createLedger(userId, req);
        List<LedgerMember> members = ledgerService.getMembers(userId, created.getId());
        return Result.ok(toLedgerResponse(created, members));
    }

    @Operation(summary = "获取账本列表", description = "获取当前用户参与的全部账本",
               security = @SecurityRequirement(name = "JWT"))
    @GetMapping
    public Result<List<LedgerResponse>> listLedgers() {
        Long userId = getCurrentUserId();
        List<Ledger> ledgers = ledgerService.listLedgers(userId);
        List<LedgerResponse> responses = ledgers.stream()
                .map(l -> toLedgerResponse(l, List.of()))
                .toList();
        return Result.ok(responses);
    }

    @Operation(summary = "获取账本详情", description = "获取账本信息及成员列表",
               security = @SecurityRequirement(name = "JWT"))
    @GetMapping("/{id}")
    public Result<LedgerResponse> getLedgerDetail(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        Ledger ledger = ledgerService.getLedgerDetail(userId, id);
        List<LedgerMember> members = ledgerService.getMembers(userId, id);
        return Result.ok(toLedgerResponse(ledger, members));
    }

    @Operation(summary = "修改账本设置", description = "修改账本名称/描述/图标/颜色",
               security = @SecurityRequirement(name = "JWT"))
    @PutMapping("/{id}")
    public Result<LedgerResponse> updateLedger(@PathVariable Long id,
                                               @Valid @RequestBody UpdateLedgerRequest req) {
        Long userId = getCurrentUserId();
        Ledger updated = ledgerService.updateLedger(userId, id, req);
        List<LedgerMember> members = ledgerService.getMembers(userId, id);
        return Result.ok(toLedgerResponse(updated, members));
    }

    @Operation(summary = "删除账本", description = "物理删除账本（owner专属）",
               security = @SecurityRequirement(name = "JWT"))
    @DeleteMapping("/{id}")
    public Result<Void> deleteLedger(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        ledgerService.deleteLedger(userId, id);
        return Result.ok();
    }

    @Operation(summary = "归档账本", description = "归档账本，对成员隐藏（owner专属）",
               security = @SecurityRequirement(name = "JWT"))
    @PostMapping("/{id}/archive")
    public Result<Void> archiveLedger(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        ledgerService.archiveLedger(userId, id);
        return Result.ok();
    }

    @Operation(summary = "恢复账本", description = "恢复已归档账本（owner专属）",
               security = @SecurityRequirement(name = "JWT"))
    @PostMapping("/{id}/unarchive")
    public Result<Void> unarchiveLedger(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        ledgerService.unarchiveLedger(userId, id);
        return Result.ok();
    }

    @Operation(summary = "转让账本", description = "将账本 owner 转让给其他成员",
               security = @SecurityRequirement(name = "JWT"))
    @PostMapping("/{id}/transfer")
    public Result<LedgerResponse> transferLedger(@PathVariable Long id,
                                                 @Valid @RequestBody TransferLedgerRequest req) {
        Long userId = getCurrentUserId();
        Ledger updated = ledgerService.transferLedger(userId, id, req);
        List<LedgerMember> members = ledgerService.getMembers(userId, id);
        return Result.ok(toLedgerResponse(updated, members));
    }

    // ==================== 成员管理 ====================

    @Operation(summary = "获取成员列表", description = "获取账本的成员列表",
               security = @SecurityRequirement(name = "JWT"))
    @GetMapping("/{id}/members")
    public Result<List<MemberResponse>> getMembers(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        List<LedgerMember> members = ledgerService.getMembers(userId, id);
        return Result.ok(toMemberResponses(members));
    }

    @Operation(summary = "添加成员", description = "通过手机号或邮箱搜索并添加成员",
               security = @SecurityRequirement(name = "JWT"))
    @PostMapping("/{id}/members")
    public Result<Void> addMember(@PathVariable Long id,
                                  @Valid @RequestBody AddMemberRequest req) {
        Long userId = getCurrentUserId();
        ledgerService.addMember(userId, id, req);
        return Result.ok();
    }

    @Operation(summary = "修改成员角色", description = "修改账本成员的角色（待实现）",
               security = @SecurityRequirement(name = "JWT"))
    @PutMapping("/{id}/members/{userId}/role")
    public Result<Void> updateMemberRole(@PathVariable Long id,
                                         @PathVariable Long userId,
                                         @Valid @RequestBody UpdateMemberRoleRequest req) {
        Long currentUserId = getCurrentUserId();
        ledgerService.updateMemberRole(currentUserId, id, userId, req);
        return Result.ok();
    }

    @Operation(summary = "移除成员", description = "从账本中移除成员（待实现）",
               security = @SecurityRequirement(name = "JWT"))
    @DeleteMapping("/{id}/members/{userId}")
    public Result<Void> removeMember(@PathVariable Long id,
                                     @PathVariable Long userId) {
        Long currentUserId = getCurrentUserId();
        ledgerService.removeMember(currentUserId, id, userId);
        return Result.ok();
    }

    // ==================== 邀请码 ====================

    @Operation(summary = "查询邀请码列表", description = "查询账本的所有邀请码（owner/admin专属）",
               security = @SecurityRequirement(name = "JWT"))
    @GetMapping("/{id}/invitations")
    public Result<List<InvitationCode>> getInvitations(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        List<InvitationCode> invitations = ledgerService.getInvitations(userId, id);
        return Result.ok(invitations);
    }

    @Operation(summary = "生成邀请码", description = "生成账本邀请码（owner/admin专属）",
               security = @SecurityRequirement(name = "JWT"))
    @PostMapping("/{id}/invitations")
    public Result<InvitationCode> createInvitation(@PathVariable Long id,
                                                   @Valid @RequestBody CreateInvitationRequest req) {
        Long userId = getCurrentUserId();
        //id是账本id
        InvitationCode invitation = ledgerService.createInvitation(userId, id, req);
        return Result.ok(invitation);
    }

    @Operation(summary = "通过邀请码加入", description = "使用邀请码加入账本",
               security = @SecurityRequirement(name = "JWT"))
    @PostMapping("/invitations/join")
    public Result<Void> joinByInvitation(@Valid @RequestBody JoinByInvitationRequest req) {
        Long userId = getCurrentUserId();
        ledgerService.joinByInvitation(userId, req);
        return Result.ok();
    }

    @Operation(summary = "失效邀请码", description = "使邀请码失效（owner/admin专属）",
               security = @SecurityRequirement(name = "JWT"))
    @PutMapping("/{id}/invitations/{codeId}/revoke")
    public Result<Void> revokeInvitation(@PathVariable Long id, @PathVariable Long codeId) {
        Long userId = getCurrentUserId();
        ledgerService.revokeInvitation(userId, id, codeId);
        return Result.ok();
    }

    // ==================== 私有方法 ====================

    private Long getCurrentUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录或登录已失效");
        }
        return userId;
    }

    private LedgerResponse toLedgerResponse(Ledger ledger, List<LedgerMember> members) {
        LedgerResponse resp = new LedgerResponse();
        resp.setId(ledger.getId());
        resp.setName(ledger.getName());
        resp.setDescription(ledger.getDescription());
        resp.setType(ledger.getType() == null ? null : ledger.getType().getCode());
        resp.setIcon(ledger.getIcon());
        resp.setColor(ledger.getColor());
        resp.setStatus(ledger.getStatus());
        resp.setOwnerId(ledger.getOwnerId());
        resp.setCreatedAt(ledger.getCreatedAt());
        resp.setMembers(toMemberResponses(members));
        return resp;
    }

    private List<MemberResponse> toMemberResponses(List<LedgerMember> members) {
        List<Long> userIds = members.stream().map(LedgerMember::getUserId).toList();
        Map<Long, User> userMap = ledgerService.getUsersMap(userIds);
        return members.stream().map(m -> {
            MemberResponse resp = new MemberResponse();
            resp.setUserId(m.getUserId());
            resp.setRole(m.getRole() == null ? null : m.getRole().getCode());
            resp.setJoinedAt(m.getJoinedAt());
            User user = userMap.get(m.getUserId());
            if (user != null) {
                resp.setNickname(user.getNickname());
                resp.setAvatar(user.getAvatar());
            }
            return resp;
        }).toList();
    }
}
