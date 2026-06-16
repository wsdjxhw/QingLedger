package com.qingledger.service.ledger;

import com.qingledger.dto.request.*;
import com.qingledger.entity.InvitationCode;
import com.qingledger.entity.Ledger;
import com.qingledger.entity.LedgerMember;

import com.qingledger.entity.User;
import java.util.List;
import java.util.Map;

public interface LedgerService {

    Ledger createLedger(Long userId, CreateLedgerRequest req);

    List<Ledger> listLedgers(Long userId);

    Ledger getLedgerDetail(Long userId, Long ledgerId);

    Ledger updateLedger(Long userId, Long ledgerId, UpdateLedgerRequest req);

    void deleteLedger(Long userId, Long ledgerId);

    void archiveLedger(Long userId, Long ledgerId);

    void unarchiveLedger(Long userId, Long ledgerId);

    Ledger transferLedger(Long userId, Long ledgerId, TransferLedgerRequest req);

    // ==================== 成员管理 ====================

    List<LedgerMember> getMembers(Long userId, Long ledgerId);

    void addMember(Long userId, Long ledgerId, AddMemberRequest req);

    void updateMemberRole(Long userId, Long ledgerId, Long targetUserId, UpdateMemberRoleRequest req);

    void removeMember(Long userId, Long ledgerId, Long targetUserId);

    // ==================== 邀请码 ====================

    List<InvitationCode> getInvitations(Long userId, Long ledgerId);

    InvitationCode createInvitation(Long userId, Long ledgerId, CreateInvitationRequest req);

    void joinByInvitation(Long userId, JoinByInvitationRequest req);

    void revokeInvitation(Long userId, Long ledgerId, Long codeId);

    // ==================== 查询辅助 ====================

    Map<Long, User> getUsersMap(List<Long> userIds);

    User getUserById(Long userId);
}
