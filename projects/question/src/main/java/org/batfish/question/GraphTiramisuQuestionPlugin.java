package org.batfish.question;

import com.google.auto.service.AutoService;
import org.batfish.common.Answerer;
import org.batfish.common.plugin.IBatfish;
import org.batfish.common.plugin.Plugin;
import org.batfish.datamodel.answers.AnswerElement;
import org.batfish.datamodel.questions.Question;
import org.batfish.datamodel.questions.smt.HeaderLocationQuestion;

@AutoService(Plugin.class)
public class GraphTiramisuQuestionPlugin extends QuestionPlugin {

  @Override
  protected Answerer createAnswerer(Question question, IBatfish batfish) {
    return new GraphTiramisuAnswerer(question, batfish);
  }

  @Override
  protected Question createQuestion() {
    return new GraphTiramisuQuestion();
  }

  public static class GraphTiramisuAnswerer extends Answerer {

    public GraphTiramisuAnswerer(Question question, IBatfish batfish) {
      super(question, batfish);
    }

    @Override
    public AnswerElement answer() {
      GraphTiramisuQuestion q = (GraphTiramisuQuestion) _question;
      return _batfish.tiramisu(q);
    }
  }

  public static class GraphTiramisuQuestion extends HeaderLocationQuestion {

    @Override
    public boolean getDataPlane() {
      return false;
    }

    @Override
    public String getName() {
      return "tiramisu";
    }

    @Override
    public String prettyPrint() {
        String retString =
            String.format("tiramisu %s", super.prettyPrintParams());
        return retString;
    }
  }
}