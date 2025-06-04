package com.fitness.activityservice.repository;

import com.fitness.activityservice.model.Activity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ActivityRepository extends MongoRepository<Activity, String> {

    List<Activity> findByUserId(String userId);
}
