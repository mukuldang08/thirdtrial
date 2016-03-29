package com.twilio.survey.controllers;

import com.twilio.sdk.verbs.*;
import com.twilio.survey.models.Question;
import com.twilio.survey.models.Response;
import com.twilio.survey.models.Survey;
import com.twilio.survey.repositories.QuestionRepository;
import com.twilio.survey.repositories.ResponseRepository;
import com.twilio.survey.services.QuestionService;
import com.twilio.survey.services.ResponseService;
import com.twilio.survey.util.ResponseParser;
import com.twilio.survey.util.TwiMLResponseBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

@Controller
public class ResponseController {
  @Autowired
  private QuestionRepository questionRepository;
  private QuestionService questionService;
  @Autowired
  private ResponseRepository responseRepository;
  private ResponseService responseService;

  public ResponseController() {}

  /**
   * End point that saves a question response and redirects the call to the next question,
   * if one is available.
   */
  @RequestMapping(value = "/save_response", method = RequestMethod.POST)
  public void readQuestion(HttpServletRequest request, HttpServletResponse response) throws Exception{
    PrintWriter responseWriter = response.getWriter();
    this.questionService = new QuestionService(questionRepository);
    this.responseService = new ResponseService(responseRepository);

    Question currentQuestion = getQuestionFromRequest(request);
    Survey survey = currentQuestion.getSurvey();
    persistResponse(new ResponseParser(currentQuestion, request).parse());

    if (survey.isLastQuestion(currentQuestion)) {
      String message = "Tank you for taking the " + survey.getTitle() + " survey. Good Bye";
      responseWriter.print(new TwiMLResponseBuilder().writeContent(request, message, true).asString());
    } else {
      responseWriter.print(redirectToNextQuestion(survey.getNextQuestionNumber(currentQuestion), survey));
    }
    response.setContentType("application/xml");
  }

  private String redirectToNextQuestion(int nextQuestionNumber, Survey survey) throws TwiMLException {
    String nextQuestionURL = "/question?survey=" + survey.getId() + "&question=" + nextQuestionNumber;
    return new TwiMLResponseBuilder().redirect(nextQuestionURL).asString();
  }

  private void persistResponse(Response questionResponse) {
    Question currentQuestion = questionResponse.getQuestion();
    Response previousResponse = responseService.getBySessionSidAndQuestion(questionResponse.getSessionSid(), currentQuestion);
    if(previousResponse!=null){
      // it's already answered. That's an update from Twilio API (Transcriptions, for instance)
      questionResponse.setId(previousResponse.getId());
    }

    /** creates the question response on the db */
    responseService.save(questionResponse);
  }

  private Question getQuestionFromRequest(HttpServletRequest request) {
    return questionService.find(Long.parseLong(request.getParameter("qid")));
  }
}
