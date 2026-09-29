package com.ffs.efdl.repository;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.ffs.efdl.model.User;
import com.ffs.efdl.model.UserType;

/** Lecture seule de la collection {@code user}. */
public interface UserRepository extends MongoRepository<User, ObjectId> {

    Optional<User> findFirstByExternalId(String externalId);

    List<User> findByUsernameAndSchoolExternalId(String username, String schoolExternalId);

    List<User> findByEmailAndSchoolExternalId(String email, String schoolExternalId);

    List<User> findByPhoneAndSchoolExternalId(String phone, String schoolExternalId);

    /** Recherche toutes écoles confondues (repli ADMIN de la connexion). */
    List<User> findByUsername(String username);

    /** Base de UserService.getByType : utilisateurs d'un type dans l'école, tri final par date de création. */
    List<User> findBySchoolExternalIdAndTypeOrderByCreationDateAsc(String schoolExternalId, UserType type);
}
