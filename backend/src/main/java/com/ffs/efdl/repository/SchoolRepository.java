package com.ffs.efdl.repository;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.ffs.efdl.model.School;

/** Lecture seule de la collection {@code school}. */
public interface SchoolRepository extends MongoRepository<School, ObjectId> {

    Optional<School> findFirstByDomainName(String domainName);

    Optional<School> findFirstByExternalId(String externalId);
}
