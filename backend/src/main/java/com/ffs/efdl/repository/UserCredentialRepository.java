package com.ffs.efdl.repository;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.ffs.efdl.model.UserCredential;

/** Nouvelle collection {@code user_credential} (lecture/écriture). */
public interface UserCredentialRepository extends MongoRepository<UserCredential, ObjectId> {

    Optional<UserCredential> findFirstByUserExternalId(String userExternalId);
}
