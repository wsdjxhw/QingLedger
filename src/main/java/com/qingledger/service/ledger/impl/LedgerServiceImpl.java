package com.qingledger.service.ledger.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qingledger.common.BusinessException;
import com.qingledger.dto.request.*;
import com.qingledger.entity.InvitationCode;
import com.qingledger.entity.Ledger;
import com.qingledger.entity.LedgerMember;
import com.qingledger.entity.Transaction;
import com.qingledger.entity.User;
import com.qingledger.entity.UserAuth;
import com.qingledger.enums.LedgerType;
import com.qingledger.enums.MemberRole;
import com.qingledger.mapper.InvitationCodeMapper;
import com.qingledger.mapper.LedgerMapper;
import com.qingledger.mapper.TransactionMapper;
import com.qingledger.mapper.LedgerMemberMapper;
import com.qingledger.mapper.UserAuthMapper;
import com.qingledger.mapper.UserMapper;
import com.qingledger.service.ledger.LedgerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LedgerServiceImpl implements LedgerService {

    private static final String MSG_LEDGER_NOT_FOUND = "账本不存在";
    private static final String MSG_NOT_MEMBER = "你不是该账本的成员";
    private static final String MSG_PERMISSION_DENIED = "权限不足";
    private static final String MSG_TARGET_NOT_MEMBER = "目标用户不是该账本成员";
    private static final String MSG_ALREADY_MEMBER = "该用户已是账本成员";
    private static final String MSG_CANNOT_REMOVE_SELF = "不能移除自己";
    private static final String MSG_CANNOT_OPERATE_OWNER = "不能操作账本创建者";
    private static final String MSG_CANNOT_ADD_SELF = "不能把自己加为成员";
    private static final String MSG_USER_NOT_FOUND = "未找到该用户";
    private static final String MSG_INVITATION_INVALID = "邀请码无效或已用完";
    private static final String MSG_INVITATION_NOT_FOUND = "邀请码不存在";
    private static final String MSG_LEDGER_ARCHIVED = "账本已归档";

    private static final Map<MemberRole, Integer> ROLE_ORDER = Map.of(
            MemberRole.OWNER, 3,
            MemberRole.ADMIN, 2,
            MemberRole.MEMBER, 1
    );

    private static final String INVITATION_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITATION_CODE_LENGTH = 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final LedgerMapper ledgerMapper;
    private final LedgerMemberMapper ledgerMemberMapper;
    private final InvitationCodeMapper invitationCodeMapper;
    private final TransactionMapper transactionMapper;
    private final UserMapper userMapper;
    private final UserAuthMapper userAuthMapper;

    public LedgerServiceImpl(LedgerMapper ledgerMapper,
                             LedgerMemberMapper ledgerMemberMapper,
                             InvitationCodeMapper invitationCodeMapper,
                             TransactionMapper transactionMapper,
                             UserMapper userMapper,
                             UserAuthMapper userAuthMapper) {
        this.ledgerMapper = ledgerMapper;
        this.ledgerMemberMapper = ledgerMemberMapper;
        this.invitationCodeMapper = invitationCodeMapper;
        this.transactionMapper = transactionMapper;
        this.userMapper = userMapper;
        this.userAuthMapper = userAuthMapper;
    }

    // ==================== 账本 CRUD ====================

    @Override
    @Transactional
    public Ledger createLedger(Long userId, CreateLedgerRequest req) {
        Ledger ledger = new Ledger();
        ledger.setName(req.getName());
        ledger.setDescription(req.getDescription());
        ledger.setOwnerId(userId);
        ledger.setType(req.getType());
        ledger.setIcon(req.getIcon());
        ledger.setColor(req.getColor());
        ledger.setStatus(1);

        try {
            ledgerMapper.insert(ledger);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(400, "创建账本失败");
        }

        // 添加创建者为 owner
        LedgerMember owner = new LedgerMember();
        owner.setLedgerId(ledger.getId());
        owner.setUserId(userId);
        owner.setRole(MemberRole.OWNER);
        ledgerMemberMapper.insert(owner);

        // 共享账本时添加初始成员
        if (req.getType() == LedgerType.SHARED && req.getMemberIds() != null) {
            for (Long memberId : req.getMemberIds()) {
                if (memberId.equals(userId)) continue;
                addMemberToLedger(ledger.getId(), memberId);
            }
        }

        return ledgerMapper.selectById(ledger.getId());
    }

    @Override
    public List<Ledger> listLedgers(Long userId) {
        return ledgerMapper.selectList(
                Wrappers.<Ledger>query()
                        .apply("id IN (SELECT ledger_id FROM ledger_member WHERE user_id = {0})", userId)
                        .orderByDesc("created_at")
        );
    }

    @Override
    public Ledger getLedgerDetail(Long userId, Long ledgerId) {
        Ledger ledger = getLedgerOrThrow(ledgerId);
        getMemberOrThrow(ledgerId, userId);
        return ledger;
    }

    @Override
    @Transactional
    public Ledger updateLedger(Long userId, Long ledgerId, UpdateLedgerRequest req) {
        Ledger ledger = getLedgerOrThrow(ledgerId);
        LedgerMember member = getMemberOrThrow(ledgerId, userId);
        requireRoleAtLeast(member, MemberRole.ADMIN);

        if (isArchived(ledger)) {
            throw new BusinessException(400, MSG_LEDGER_ARCHIVED);
        }

        if (req.getName() != null) {
            ledger.setName(req.getName());
        }
        if (req.getDescription() != null) {
            ledger.setDescription(req.getDescription().isEmpty() ? null : req.getDescription());
        }
        if (req.getIcon() != null) {
            ledger.setIcon(req.getIcon().isEmpty() ? null : req.getIcon());
        }
        if (req.getColor() != null) {
            ledger.setColor(req.getColor().isEmpty() ? null : req.getColor());
        }

        ledgerMapper.updateById(ledger);
        return ledgerMapper.selectById(ledgerId);
    }

    @Override
    @Transactional
    public void deleteLedger(Long userId, Long ledgerId) {
        Ledger ledger = getLedgerOrThrow(ledgerId);
        LedgerMember member = getMemberOrThrow(ledgerId, userId);
        requireRoleExactly(member, MemberRole.OWNER);

        // 删除账本下所有交易（无外键级联，需手动清理）
        transactionMapper.delete(
                Wrappers.<Transaction>query().eq("ledger_id", ledgerId)
        );
        ledgerMapper.deleteById(ledgerId);
    }

    @Override
    @Transactional
    public void archiveLedger(Long userId, Long ledgerId) {
        Ledger ledger = getLedgerOrThrow(ledgerId);
        LedgerMember member = getMemberOrThrow(ledgerId, userId);
        requireRoleExactly(member, MemberRole.OWNER);

        ledger.setStatus(0);
        ledgerMapper.updateById(ledger);
    }

    @Override
    @Transactional
    public void unarchiveLedger(Long userId, Long ledgerId) {
        Ledger ledger = getLedgerOrThrow(ledgerId);
        LedgerMember member = getMemberOrThrow(ledgerId, userId);
        requireRoleExactly(member, MemberRole.OWNER);

        ledger.setStatus(1);
        ledgerMapper.updateById(ledger);
    }

    @Override
    @Transactional
    public Ledger transferLedger(Long userId, Long ledgerId, TransferLedgerRequest req) {
        Ledger ledger = getLedgerOrThrow(ledgerId);
        LedgerMember member = getMemberOrThrow(ledgerId, userId);
        requireRoleExactly(member, MemberRole.OWNER);

        Long targetUserId = req.getTargetUserId();
        if (targetUserId.equals(userId)) {
            throw new BusinessException(400, "不能转让给自己");
        }

        LedgerMember targetMember = ledgerMemberMapper.selectOne(
                Wrappers.<LedgerMember>query()
                        .eq("ledger_id", ledgerId)
                        .eq("user_id", targetUserId)
        );
        if (targetMember == null) {
            throw new BusinessException(400, MSG_TARGET_NOT_MEMBER);
        }

        // 个人账本自动转为共享
        if (ledger.getType() == LedgerType.PERSONAL) {
            ledger.setType(LedgerType.SHARED);
        }

        // 旧 owner 降级为 admin
        member.setRole(MemberRole.ADMIN);
        ledgerMemberMapper.updateById(member);

        // 目标升级为 owner
        targetMember.setRole(MemberRole.OWNER);
        ledgerMemberMapper.updateById(targetMember);

        // 同步 ledger.owner_id
        ledger.setOwnerId(targetUserId);
        ledgerMapper.updateById(ledger);

        return ledgerMapper.selectById(ledgerId);
    }

    // ==================== 成员管理 ====================

    @Override
    public List<LedgerMember> getMembers(Long userId, Long ledgerId) {
        Ledger ledger = getLedgerOrThrow(ledgerId);

        if (ledger.getStatus() == 0) {
            LedgerMember member = ledgerMemberMapper.selectOne(
                    Wrappers.<LedgerMember>query()
                            .eq("ledger_id", ledgerId)
                            .eq("user_id", userId)
                            .eq("role", MemberRole.OWNER.name().toLowerCase())
            );
            if (member == null) {
                throw new BusinessException(403, MSG_PERMISSION_DENIED);
            }
        } else {
            getMemberOrThrow(ledgerId, userId);
        }

        return ledgerMemberMapper.selectList(
                Wrappers.<LedgerMember>query()
                        .eq("ledger_id", ledgerId)
                        .orderByAsc("role", "joined_at")
        );
    }

    @Override
    @Transactional
    public void addMember(Long userId, Long ledgerId, AddMemberRequest req) {
        Ledger ledger = getLedgerOrThrow(ledgerId);
        LedgerMember currentMember = getMemberOrThrow(ledgerId, userId);
        requireRoleAtLeast(currentMember, MemberRole.ADMIN);

        if (isArchived(ledger)) {
            throw new BusinessException(400, MSG_LEDGER_ARCHIVED);
        }

        String identifier = req.getIdentifier().trim();
        User targetUser = searchUserByIdentifier(identifier);

        if (targetUser == null) {
            throw new BusinessException(404, MSG_USER_NOT_FOUND);
        }
        if (targetUser.getId().equals(userId)) {
            throw new BusinessException(400, MSG_CANNOT_ADD_SELF);
        }

        // 检查是否已是成员
        LedgerMember existing = ledgerMemberMapper.selectOne(
                Wrappers.<LedgerMember>query()
                        .eq("ledger_id", ledgerId)
                        .eq("user_id", targetUser.getId())
        );
        if (existing != null) {
            throw new BusinessException(400, MSG_ALREADY_MEMBER);
        }

        addMemberToLedger(ledgerId, targetUser.getId());
    }

    @Override
    @Transactional
    public void updateMemberRole(Long userId, Long ledgerId, Long targetUserId, UpdateMemberRoleRequest req) {
        if(userId.equals(targetUserId)) {
            throw new BusinessException(400, "不能修改自己");
        }
        Ledger ledger = getLedgerOrThrow(ledgerId);
        if (isArchived(ledger)) {
            throw new BusinessException(400, MSG_LEDGER_ARCHIVED);
        }
        LedgerMember currentMember = getMemberOrThrow(ledgerId, userId);
        //判断是否权限足够
        LedgerMember targetMember = getMemberOrThrow(ledgerId, targetUserId);
        requireRoleAtLeast(currentMember,targetMember.getRole());
        if(currentMember.getRole() == targetMember.getRole()) {
            throw new BusinessException(400,MSG_PERMISSION_DENIED);
        }
        if(targetMember.getRole() == MemberRole.OWNER || req.getRole() == MemberRole.OWNER) {
            throw new BusinessException(400, MSG_CANNOT_OPERATE_OWNER);
        }

        targetMember.setRole(req.getRole());
        ledgerMemberMapper.updateById(targetMember);
    }

    @Override
    @Transactional
    public void removeMember(Long userId, Long ledgerId, Long targetUserId) {
        if(userId.equals(targetUserId)) {
            throw new BusinessException(400, MSG_CANNOT_REMOVE_SELF);
        }
        Ledger ledger = getLedgerOrThrow(ledgerId);
        if (isArchived(ledger)) {
            throw new BusinessException(400, MSG_LEDGER_ARCHIVED);
        }
        LedgerMember member = getMemberOrThrow(ledgerId, userId);

        LedgerMember targetMember = ledgerMemberMapper.selectOne(
                Wrappers.<LedgerMember>query()
                        .eq("ledger_id", ledgerId)
                        .eq("user_id", targetUserId)
        );
        if (targetMember == null) {
            throw new BusinessException(400, MSG_TARGET_NOT_MEMBER);
        }
        if(member.getRole() == targetMember.getRole()) {
            throw new BusinessException(400,MSG_PERMISSION_DENIED);
        }
        requireRoleAtLeast(member, targetMember.getRole());

        ledgerMemberMapper.deleteById(targetMember.getId());
    }

    // ==================== 邀请码 ====================

    @Override
    public List<InvitationCode> getInvitations(Long userId, Long ledgerId) {
        // 权限检查
        Ledger ledger = getLedgerOrThrow(ledgerId);
        LedgerMember member = getMemberOrThrow(ledgerId, userId);
        requireRoleAtLeast(member, MemberRole.ADMIN);

        // 查询该账本的所有邀请码
        return invitationCodeMapper.selectList(
            Wrappers.<InvitationCode>query()
                .eq("ledger_id", ledgerId)
                .orderByDesc("created_at")
        );
    }

    @Override
    @Transactional
    public InvitationCode createInvitation(Long userId, Long ledgerId, CreateInvitationRequest req) {
        Ledger ledger = getLedgerOrThrow(ledgerId);
        LedgerMember member = getMemberOrThrow(ledgerId, userId);
        requireRoleAtLeast(member, MemberRole.ADMIN);

        if (isArchived(ledger)) {
            throw new BusinessException(400, MSG_LEDGER_ARCHIVED);
        }

        InvitationCode invitation = new InvitationCode();
        invitation.setCode(generateInvitationCode());
        invitation.setLedgerId(ledgerId);
        invitation.setCreatorId(userId);
        invitation.setMaxUses(req.getMaxUses());
        invitation.setUseCount(0);
        invitation.setStatus("active");

        try {
            invitationCodeMapper.insert(invitation);
        } catch (DataIntegrityViolationException e) {
            // 邀请码唯一键冲突，重试一次
            invitation.setCode(generateInvitationCode());
            invitationCodeMapper.insert(invitation);
        }

        return invitation;
    }

    @Override
    @Transactional
    public void joinByInvitation(Long userId, JoinByInvitationRequest req) {
        //对用户传来的邀请码进行去空和大小写转换
        String code = req.getCode().trim().toUpperCase();

        // 1. 先查邀请码基本信息
        InvitationCode invitation = invitationCodeMapper.selectOne(
                Wrappers.<InvitationCode>query().eq("code", code)
        );
        if (invitation == null || !"active".equals(invitation.getStatus())) {
            throw new BusinessException(400, MSG_INVITATION_INVALID);
        }

        // 2. 检查是否已是成员（提前返回，不浪费原子 UPDATE）
        LedgerMember existing = ledgerMemberMapper.selectOne(
                Wrappers.<LedgerMember>query()
                        .eq("ledger_id", invitation.getLedgerId())
                        .eq("user_id", userId)
        );
        if (existing != null) {
            throw new BusinessException(400, MSG_ALREADY_MEMBER);
        }

        // 3. 原子消耗（真正的竞争点）
        int affected = invitationCodeMapper.consumeCode(code);
        if (affected <= 0) {
            throw new BusinessException(400, MSG_INVITATION_INVALID);
        }

        // 4. 加入账本
        addMemberToLedger(invitation.getLedgerId(), userId);
    }

    @Override
    @Transactional
    public void revokeInvitation(Long userId, Long ledgerId, Long codeId) {
        //判断是否是账本成员以及权限是否足够
        LedgerMember member = getMemberOrThrow(ledgerId, userId);
        requireRoleAtLeast(member, MemberRole.ADMIN);

        InvitationCode invitation = invitationCodeMapper.selectById(codeId);
        if (invitation == null || !invitation.getLedgerId().equals(ledgerId)) {
            throw new BusinessException(404, MSG_INVITATION_NOT_FOUND);
        }

        invitation.setStatus("revoked");
        invitationCodeMapper.updateById(invitation);
    }

    // ==================== 私有方法 ====================

    private Ledger getLedgerOrThrow(Long ledgerId) {
        Ledger ledger = ledgerMapper.selectById(ledgerId);
        if (ledger == null) {
            throw new BusinessException(404, MSG_LEDGER_NOT_FOUND);
        }
        return ledger;
    }

    private LedgerMember getMemberOrThrow(Long ledgerId, Long userId) {
        LedgerMember member = ledgerMemberMapper.selectOne(
                Wrappers.<LedgerMember>query()
                        .eq("ledger_id", ledgerId)
                        .eq("user_id", userId)
        );
        if (member == null) {
            throw new BusinessException(403, MSG_NOT_MEMBER);
        }
        return member;
    }

    private void requireRoleAtLeast(LedgerMember member, MemberRole minRole) {
        if (ROLE_ORDER.get(member.getRole()) < ROLE_ORDER.get(minRole)) {
            throw new BusinessException(403, MSG_PERMISSION_DENIED);
        }
    }

    private void requireRoleExactly(LedgerMember member, MemberRole role) {
        if (member.getRole() != role) {
            throw new BusinessException(403, MSG_PERMISSION_DENIED);
        }
    }

    private boolean isArchived(Ledger ledger) {
        return ledger.getStatus() != null && ledger.getStatus() == 0;
    }

    private void addMemberToLedger(Long ledgerId, Long targetUserId) {
        LedgerMember newMember = new LedgerMember();
        newMember.setLedgerId(ledgerId);
        newMember.setUserId(targetUserId);
        newMember.setRole(MemberRole.MEMBER);
        try {
            ledgerMemberMapper.insert(newMember);
        } catch (DataIntegrityViolationException e) {
            // 并发重复添加
            throw new BusinessException(400, MSG_ALREADY_MEMBER);
        }
    }

    private User searchUserByIdentifier(String identifier) {
        if (identifier.contains("@")) {
            // 邮箱：精确匹配
            UserAuth auth = userAuthMapper.selectOne(
                    Wrappers.<UserAuth>query()
                            .eq("auth_type", "EMAIL")
                            .eq("identifier", identifier)
            );
            if (auth == null) return null;
            return userMapper.selectById(auth.getUserId());
        } else {
            // 手机号：精确匹配
            UserAuth auth = userAuthMapper.selectOne(
                    Wrappers.<UserAuth>query()
                            .eq("auth_type", "PHONE")
                            .eq("identifier", identifier)
            );
            if (auth == null) return null;
            return userMapper.selectById(auth.getUserId());
        }
    }

    private String generateInvitationCode() {
        StringBuilder sb = new StringBuilder(INVITATION_CODE_LENGTH);
        for (int i = 0; i < INVITATION_CODE_LENGTH; i++) {
            sb.append(INVITATION_CHARS.charAt(SECURE_RANDOM.nextInt(INVITATION_CHARS.length())));
        }
        return sb.toString();
    }

    // ==================== 供 controller 使用的查询方法 ====================

    public Map<Long, User> getUsersMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        return users.stream().collect(Collectors.toMap(User::getId, u -> u));
    }

    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }
}
