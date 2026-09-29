package com.ffs.efdl.repository;

import java.util.Collection;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.ffs.efdl.model.Contract;

/** Lecture seule de la collection {@code Contract}. */
public interface ContractRepository extends MongoRepository<Contract, ObjectId> {

    @Query(value = "{ 'schoolExternalId': ?0, 'contractType': { $in: ?1 } }", fields = "{ 'userExternalId': 1 }")
    List<Contract> findUserIdsBySchoolAndTypes(String schoolExternalId, Collection<String> contractTypes);
}
