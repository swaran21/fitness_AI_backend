package com.fitness.aiservice.service;

import com.fitness.aiservice.model.Activity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityAIService {
    private final GeminiService geminiService;

    String generateRecommendation(Activity activity) {
        String prompt = createPromptForActivity(activity);
        String aiResponse = geminiService.getAnswers(prompt);
        log.info("Response from AI: {} " , aiResponse);
        return aiResponse;
    }

    private String createPromptForActivity(Activity activity) {
        return String.format(""" 
                  Analyze this fitness activity and provide detailed recommendations in the following format
                  {
                      "analysis" : {
                          "overall": "Overall analysis here",
                          "pace": "Pace analysis here",
                          "heartRate": "Heart rate analysis here",
                          "CaloriesBurned": "Calories Burned here"
                      },
                      "improvements": [
                          {
                              "area": "Area name",
                              "recommendation": "Detailed Recommendation"
                          }
                      ],
                      "suggestions" : [
                          {
                              "workout": "Workout name",
                              "description": "Detailed workout description"
                          }
                      ],
                      "safety": [
                          "Safety point 1",
                          "Safety point 2"
                      ]
                  }
                
                  Analyze this activity:
                  Activity Type: %s
                  Duration: %d minutes
                  calories Burned: %d
                  Additional Metrics: %s
                
                  provide detailed analysis focusing on performance, improvements, next workout suggestions, and safety guidelines
                  Ensure the response follows the EXACT JSON format shown above.
                """,
                activity.getType(),
                activity.getDuration(),
                activity.getCaloriesBurned(),
                activity.getAdditionalMetrics()
        );
    }


}
