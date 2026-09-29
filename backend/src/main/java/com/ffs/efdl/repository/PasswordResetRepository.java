package com.ffs.efdl.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.ffs.efdl.model.PasswordReset;

public interface PasswordResetRepository extends MongoRepository<PasswordReset, org.bson.types.ObjectId> {

    /** Dernière demande de l'utilisateur. */
    Optional<PasswordReset> findFirstByUserExternalIdOrderByCreatedAtDesc(String userExternalId);

    List<PasswordReset> findByUserExternalIdAndUsedFalse(String userExternalId);
}
