package com.ffs.efdl.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.ffs.efdl.model.UserGroup;

/** Lecture seule de la collection {@code user_group}. */
public interface UserGroupRepository extends MongoRepository<UserGroup, ObjectId> {

    List<UserGroup> findByExternalIdIn(Collection<String> externalIds);

    /** Équivalent de UserService.getAllUserGroups(school) (ordre naturel de la collection). */
    List<UserGroup> findBySchoolExternalId(String schoolExternalId);

    /** Groupe par externalId (sans filtre d'école). */
    Optional<UserGroup> findFirstByExternalId(String externalId);
}
