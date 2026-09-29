package com.ffs.efdl.repository;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.ffs.efdl.model.Menu;

/** Lecture seule de la collection {@code Menu}. */
public interface MenuRepository extends MongoRepository<Menu, ObjectId> {

    /** Équivalent de MenuService.getMenusBySchoolId (tri par {@code order}). */
    List<Menu> findBySchoolExternalIdOrderByOrderAsc(String schoolExternalId);

    /** Équivalent de MenuService.getMenuById(school, id). */
    Optional<Menu> findFirstBySchoolExternalIdAndExternalId(String schoolExternalId, String externalId);
}
