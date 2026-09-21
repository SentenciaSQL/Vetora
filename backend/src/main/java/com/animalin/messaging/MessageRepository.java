package com.animalin.messaging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    List<Message> findByConversationIdAndIdGreaterThanOrderByCreatedAtAsc(Long conversationId, Long afterId);

    Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(Long conversationId);

    long countByConversation_TenantIdAndReadAtIsNullAndSenderIdNot(Long tenantId, Long userId);

    long countByConversationIdAndReadAtIsNullAndSenderIdNot(Long conversationId, Long userId);

    long countByTenantIdAndCreatedAtGreaterThanEqual(Long tenantId, Instant createdAt);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(Instant from, Instant to);

    @Query("""
            select count(m) from Message m
            where m.sender.id <> :userId
              and m.conversation.tenantId = :tenantId
              and (not exists (
                    select r from ConversationReadState r
                    where r.conversation = m.conversation and r.user.id = :userId
                  )
                  or m.createdAt > (
                    select r.lastReadAt from ConversationReadState r
                    where r.conversation = m.conversation and r.user.id = :userId
                  ))
            """)
    long countUnreadForTenantUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Query("""
            select count(m) from Message m
            where m.sender.id <> :userId
              and (exists (
                    select p from m.conversation.participants p where p.id = :userId
                  )
                  or exists (
                    select o from Owner o
                    where o = m.conversation.owner and o.user.id = :userId
                  ))
              and (not exists (
                    select r from ConversationReadState r
                    where r.conversation = m.conversation and r.user.id = :userId
                  )
                  or m.createdAt > (
                    select r.lastReadAt from ConversationReadState r
                    where r.conversation = m.conversation and r.user.id = :userId
                  ))
            """)
    long countUnreadForParticipant(@Param("userId") Long userId);

    long countByConversationIdAndSenderIdNot(Long conversationId, Long userId);

    long countByConversationIdAndSenderIdNotAndCreatedAtAfter(Long conversationId, Long userId, Instant createdAt);
}
