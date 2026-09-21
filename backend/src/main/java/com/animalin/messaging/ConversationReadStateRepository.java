package com.animalin.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationReadStateRepository extends JpaRepository<ConversationReadState, Long> {
    Optional<ConversationReadState> findByConversationIdAndUserId(Long conversationId, Long userId);

    List<ConversationReadState> findByUserIdAndConversationIdIn(Long userId, Collection<Long> conversationIds);
}
