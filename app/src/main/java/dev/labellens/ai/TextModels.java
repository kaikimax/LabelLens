package dev.labellens.ai;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gives a TextModel for any deployment on the same Azure resource ("gpt-4.1", "gpt-4.1-nano",
 * later the fine-tuned model). One endpoint and key, several deployments: the deployment is
 * chosen per call through the chat options.
 */
@Component
public class TextModels {

    private final ChatModel chatModel;

    public TextModels(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public TextModel forDeployment(String deployment) {
        return new SpringAiTextModel(chatModel, deployment);
    }

    static final class SpringAiTextModel implements TextModel {

        private final ChatModel chatModel;
        private final String deployment;

        SpringAiTextModel(ChatModel chatModel, String deployment) {
            this.chatModel = chatModel;
            this.deployment = deployment;
        }

        @Override
        public ModelReply ask(String instructions, String text) {
            List<Message> messages = List.of(new SystemMessage(instructions), new UserMessage(text));
            // Temperature 0: as repeatable as possible, like the label reader.
            // OpenAiChatOptions, not the generic ChatOptions: Spring AI 2.0's OpenAiChatModel casts the
            // per-call options to its own class (ClassCastException otherwise, found on day 6).
            OpenAiChatOptions options = OpenAiChatOptions.builder().model(deployment).temperature(0.0).build();

            ChatResponse response = chatModel.call(new Prompt(messages, options));

            String answer = response.getResult().getOutput().getText();
            Usage usage = response.getMetadata().getUsage();
            String model = response.getMetadata().getModel();
            return new ModelReply(
                    answer,
                    model != null && !model.isBlank() ? model : deployment,
                    usage != null ? usage.getPromptTokens() : null,
                    usage != null ? usage.getCompletionTokens() : null);
        }

        @Override
        public String name() {
            return deployment;
        }
    }
}
