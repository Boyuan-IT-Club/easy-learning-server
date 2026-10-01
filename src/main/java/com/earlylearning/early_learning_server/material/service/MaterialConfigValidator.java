package com.earlylearning.early_learning_server.material.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.earlylearning.early_learning_server.common.error.ApiErrorDetails;
import com.earlylearning.early_learning_server.common.error.BusinessException;
import com.earlylearning.early_learning_server.common.error.ErrorCode;
import com.earlylearning.early_learning_server.material.model.config.ActivityConfig;
import com.earlylearning.early_learning_server.material.model.config.NarrationActivity;
import com.earlylearning.early_learning_server.material.model.config.QuestioningActivity;
import com.earlylearning.early_learning_server.material.model.config.SortingActivity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * config.json 的语义校验与转换：按冻结契约逐字段检查，产出编码化后的活动配置。
 *
 * <p>错误定位遵守契约约定：field_path 相对 config.json 根；文件引用失败同时给
 * details.file_name，评分条目映射失败给 details.rubric_item_code。一次只报第一个失败。
 *
 * <p>文件引用的解析交给 {@link FileRefResolver}：上传前用「只校验存在」的解析器完整跑一遍，
 * 上传后用「文件名到编号」的映射再跑一遍得到最终配置——两遍走同一条校验路径，
 * 不存在第二套规则。
 */
@Component
public class MaterialConfigValidator {

    /** 解析包内文件名引用。fieldPath 供错误定位；引用不存在时由解析器抛 INVALID_RESOURCE_REFERENCE。 */
    public interface FileRefResolver {

        String fileCodeOf(String fileName, String fieldPath);
    }

    private static final Set<String> ROOT_KEYS = Set.of(
            "official_material_code", "content_version", "name", "schema_version", "story_context", "activities");
    private static final Set<String> ACTIVITY_KEYS = Set.of("activity_id", "type", "config");
    private static final Set<String> SORTING_KEYS = Set.of("items", "correct_order");
    private static final Set<String> SORTING_ITEM_KEYS = Set.of("item_id", "file_name");
    private static final Set<String> QUESTIONING_KEYS = Set.of("questions");
    private static final Set<String> QUESTION_KEYS = Set.of("question_id", "text");
    private static final Set<String> OPTIONAL_QUESTION_KEYS = Set.of("hint", "grammar");
    private static final Set<String> NARRATION_KEYS = Set.of("content_items");
    private static final Set<String> OPTIONAL_NARRATION_KEYS = Set.of("audio_file_name");
    private static final Set<String> CONTENT_ITEM_KEYS = Set.of("content_item_id", "image_file_names", "rubric_item_code");

    private static final String CODE_PATTERN = "^[A-Za-z0-9_-]+$";
    private static final String VERSION_PATTERN = "^[A-Za-z0-9][A-Za-z0-9._-]*$";
    private static final int CODE_MAX = 128;
    private static final int VERSION_MAX = 64;
    private static final int NAME_MAX = 255;

    /**
     * 校验并转换整份配置。
     *
     * @param packageFileNames 包内实际存在的媒体文件名，引用必须与其中一项完全一致
     * @param rubricItemCodes 统一评分规则里可被图片分组映射的条目编号
     * @param knownGrammarCodes 当前存在定义的语法编号
     * @param resolver 包内文件名到 CF_ 编号的解析
     */
    public ValidatedMaterial build(JsonNode root,
                                   Set<String> packageFileNames,
                                   Set<String> rubricItemCodes,
                                   Set<String> knownGrammarCodes,
                                   FileRefResolver resolver) {
        requireObject(root, "/");
        requireExactKeys(root, ROOT_KEYS, "");

        String code = requireCode(root, "official_material_code");
        String version = requireVersion(root, "content_version");
        String name = requireName(root);

        JsonNode schemaVersion = root.get("schema_version");
        if (!schemaVersion.isIntegralNumber() || schemaVersion.asInt() != ActivityConfig.SCHEMA_VERSION) {
            throw configError("/schema_version", "schema_version 必须为 " + ActivityConfig.SCHEMA_VERSION);
        }
        String storyContext = requireText(root, "story_context", "");

        List<com.earlylearning.early_learning_server.material.model.config.Activity> activities =
                buildActivities(root.get("activities"), packageFileNames, rubricItemCodes, knownGrammarCodes, resolver);

        return new ValidatedMaterial(code, version, name,
                new ActivityConfig(ActivityConfig.SCHEMA_VERSION, storyContext, activities));
    }

    /** 收集全部题目引用的语法编号；发布校验存在性、下载展开定义都用它。 */
    public Set<String> referencedGrammarCodes(JsonNode root) {
        Set<String> codes = new HashSet<>();
        for (JsonNode activity : root.path("activities")) {
            if (!"QUESTION_ANSWERING".equals(activity.path("type").asString(null))) {
                continue;
            }
            for (JsonNode question : activity.path("config").path("questions")) {
                for (JsonNode grammar : question.path("grammar")) {
                    codes.add(grammar.asString());
                }
            }
        }
        return codes;
    }

    /** 从冻结配置里收集全部 CF_ 编号：排序图片、故事音频与故事图片分组。 */
    public Set<String> referencedFileCodes(JsonNode root) {
        Set<String> codes = new java.util.LinkedHashSet<>();
        for (JsonNode activity : root.path("activities")) {
            String type = activity.path("type").asString(null);
            if (SortingActivity.TYPE.equals(type)) {
                for (JsonNode item : activity.path("config").path("items")) {
                    codes.add(item.path("file_code").asString());
                }
            } else if (NarrationActivity.TYPE.equals(type)) {
                JsonNode config = activity.path("config");
                if (config.has("audio_file_code")) {
                    codes.add(config.get("audio_file_code").asString());
                }
                for (JsonNode contentItem : activity.path("config").path("content_items")) {
                    for (JsonNode fileCode : contentItem.path("image_file_codes")) {
                        codes.add(fileCode.asString());
                    }
                }
            }
        }
        return codes;
    }

    private List<com.earlylearning.early_learning_server.material.model.config.Activity> buildActivities(
            JsonNode activitiesNode, Set<String> packageFileNames, Set<String> rubricItemCodes,
            Set<String> knownGrammarCodes, FileRefResolver resolver) {
        if (!activitiesNode.isArray() || activitiesNode.isEmpty()) {
            throw configError("/activities", "activities 必须是非空数组");
        }
        List<com.earlylearning.early_learning_server.material.model.config.Activity> activities = new ArrayList<>();
        Set<String> activityIds = new HashSet<>();
        Set<String> seenTypes = new HashSet<>();
        int narrationCount = 0;
        for (int i = 0; i < activitiesNode.size(); i++) {
            JsonNode activity = activitiesNode.get(i);
            String path = "/activities/" + i;
            requireObject(activity, path);
            requireExactKeys(activity, ACTIVITY_KEYS, path);

            String activityId = requireNonBlankText(activity, "activity_id", path);
            if (!activityIds.add(activityId)) {
                throw configError(path + "/activity_id", "activity_id 不得重复");
            }
            JsonNode typeNode = activity.get("type");
            if (!typeNode.isTextual()) {
                throw configError(path + "/type", "type 必须是字符串");
            }
            String type = typeNode.asString();
            if (!seenTypes.add(type)) {
                throw configError(path + "/type", "每类活动最多一个");
            }
            JsonNode config = activity.get("config");
            requireObject(config, path + "/config");

            switch (type) {
                case SortingActivity.TYPE -> activities.add(buildSorting(activityId, config, path, packageFileNames, resolver));
                case QuestioningActivity.TYPE -> activities.add(buildQuestioning(activityId, config, path, knownGrammarCodes));
                case NarrationActivity.TYPE -> {
                    activities.add(buildNarration(activityId, config, path, packageFileNames, rubricItemCodes, resolver));
                    narrationCount++;
                }
                default -> throw configError(path + "/type", "未知活动类型");
            }
        }
        if (narrationCount != 1) {
            throw new BusinessException(ErrorCode.STORY_NARRATION_REQUIRED,
                    "评估材料必须恰好包含一个故事叙述活动",
                    ApiErrorDetails.atField("/activities"));
        }
        return List.copyOf(activities);
    }

    private SortingActivity buildSorting(String activityId, JsonNode config, String path,
                                         Set<String> packageFileNames, FileRefResolver resolver) {
        requireExactKeys(config, SORTING_KEYS, path + "/config");
        JsonNode items = config.get("items");
        if (!items.isArray() || items.isEmpty()) {
            throw configError(path + "/config/items", "items 必须是非空数组");
        }
        Set<String> itemIds = new HashSet<>();
        List<SortingActivity.Config.Item> builtItems = new ArrayList<>();
        List<String> builtOrder = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            String itemPath = path + "/config/items/" + i;
            requireObject(item, itemPath);
            requireExactKeys(item, SORTING_ITEM_KEYS, itemPath);
            String itemId = requireNonBlankText(item, "item_id", itemPath);
            if (!itemIds.add(itemId)) {
                throw configError(itemPath + "/item_id", "item_id 不得重复");
            }
            String fileName = requireNonBlankText(item, "file_name", itemPath);
            builtItems.add(new SortingActivity.Config.Item(itemId, resolver.fileCodeOf(fileName, itemPath + "/file_name")));
        }
        JsonNode order = config.get("correct_order");
        if (!order.isArray() || order.size() != builtItems.size()) {
            throw configError(path + "/config/correct_order", "correct_order 必须与 items 一一对应");
        }
        Set<String> ordered = new HashSet<>();
        for (int i = 0; i < order.size(); i++) {
            JsonNode entry = order.get(i);
            if (!entry.isTextual()) {
                throw configError(path + "/config/correct_order", "correct_order 元素必须是字符串");
            }
            String entryId = entry.asString();
            if (!ordered.add(entryId)) {
                throw configError(path + "/config/correct_order", "correct_order 不得重复");
            }
            builtOrder.add(entryId);
        }
        if (!ordered.equals(itemIds)) {
            throw configError(path + "/config/correct_order", "correct_order 与 items 不一致");
        }
        return new SortingActivity(activityId, SortingActivity.TYPE,
                new SortingActivity.Config(List.copyOf(builtItems), List.copyOf(builtOrder)));
    }

    private QuestioningActivity buildQuestioning(String activityId, JsonNode config, String path,
                                               Set<String> knownGrammarCodes) {
        requireExactKeys(config, QUESTIONING_KEYS, path + "/config");
        JsonNode questions = config.get("questions");
        if (!questions.isArray() || questions.isEmpty()) {
            throw configError(path + "/config/questions", "questions 必须是非空数组");
        }
        Set<String> questionIds = new HashSet<>();
        List<QuestioningActivity.Config.Question> built = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            JsonNode question = questions.get(i);
            String questionPath = path + "/config/questions/" + i;
            requireObject(question, questionPath);
            requireExactKeys(question, QUESTION_KEYS, OPTIONAL_QUESTION_KEYS, questionPath);
            String questionId = requireNonBlankText(question, "question_id", questionPath);
            if (!questionIds.add(questionId)) {
                throw configError(questionPath + "/question_id", "question_id 不得重复");
            }
            String text = requireNonBlankText(question, "text", questionPath);
            String hint = question.has("hint") ? requireText(question, "hint", questionPath) : null;
            List<String> grammarCodes = null;
            if (question.has("grammar")) {
                JsonNode grammar = question.get("grammar");
                if (!grammar.isArray()) {
                    throw configError(questionPath + "/grammar", "grammar 必须是数组");
                }
                Set<String> grammarSeen = new HashSet<>();
                grammarCodes = new ArrayList<>();
                for (int j = 0; j < grammar.size(); j++) {
                    JsonNode entry = grammar.get(j);
                    if (!entry.isTextual()) {
                        throw configError(questionPath + "/grammar", "grammar 元素必须是字符串");
                    }
                    String grammarCode = entry.asString();
                    if (!grammarCode.matches(CODE_PATTERN) || grammarCode.length() > CODE_MAX) {
                        throw configError(questionPath + "/grammar", "语法编号格式不合法");
                    }
                    if (!grammarSeen.add(grammarCode)) {
                        throw configError(questionPath + "/grammar", "grammar 不得重复");
                    }
                    if (!knownGrammarCodes.contains(grammarCode)) {
                        throw new BusinessException(ErrorCode.INVALID_GRAMMAR_REFERENCE,
                                "配置引用的语法不存在",
                                new ApiErrorDetails(questionPath + "/grammar", null, null, null, null, null, null));
                    }
                    grammarCodes.add(grammarCode);
                }
                grammarCodes = List.copyOf(grammarCodes);
            }
            built.add(new QuestioningActivity.Config.Question(questionId, text, hint, grammarCodes));
        }
        return new QuestioningActivity(activityId, QuestioningActivity.TYPE,
                new QuestioningActivity.Config(List.copyOf(built)));
    }

    private NarrationActivity buildNarration(String activityId, JsonNode config, String path,
                                              Set<String> packageFileNames, Set<String> rubricItemCodes,
                                              FileRefResolver resolver) {
        requireExactKeys(config, NARRATION_KEYS, OPTIONAL_NARRATION_KEYS, path + "/config");
        String audioCode = null;
        if (config.has("audio_file_name")) {
            String audioName = requireNonBlankText(config, "audio_file_name", path + "/config");
            audioCode = resolver.fileCodeOf(audioName, path + "/config/audio_file_name");
        }

        JsonNode contentItems = config.get("content_items");
        if (!contentItems.isArray() || contentItems.isEmpty()) {
            throw configError(path + "/config/content_items", "content_items 必须是非空数组");
        }
        Set<String> contentItemIds = new HashSet<>();
        List<NarrationActivity.Config.ContentItem> built = new ArrayList<>();
        for (int i = 0; i < contentItems.size(); i++) {
            JsonNode contentItem = contentItems.get(i);
            String itemPath = path + "/config/content_items/" + i;
            requireObject(contentItem, itemPath);
            requireExactKeys(contentItem, CONTENT_ITEM_KEYS, itemPath);
            String contentItemId = requireNonBlankText(contentItem, "content_item_id", itemPath);
            if (!contentItemIds.add(contentItemId)) {
                throw configError(itemPath + "/content_item_id", "content_item_id 不得重复");
            }
            JsonNode imageNames = contentItem.get("image_file_names");
            if (!imageNames.isArray() || imageNames.isEmpty()) {
                throw configError(itemPath + "/image_file_names", "image_file_names 必须是非空数组");
            }
            Set<String> names = new HashSet<>();
            List<String> imageCodes = new ArrayList<>();
            for (int j = 0; j < imageNames.size(); j++) {
                JsonNode entry = imageNames.get(j);
                if (!entry.isTextual()) {
                    throw configError(itemPath + "/image_file_names", "image_file_names 元素必须是字符串");
                }
                String imageName = entry.asString();
                if (!names.add(imageName)) {
                    throw configError(itemPath + "/image_file_names", "同一分组的图片不得重复");
                }
                imageCodes.add(resolver.fileCodeOf(imageName, itemPath + "/image_file_names/" + j));
            }
            String rubricItemCode = requireNonBlankText(contentItem, "rubric_item_code", itemPath);
            if (!rubricItemCodes.contains(rubricItemCode)) {
                throw new BusinessException(ErrorCode.INVALID_RUBRIC_MAPPING,
                        "评分条目不在统一评分规则中",
                        new ApiErrorDetails(itemPath + "/rubric_item_code", null, rubricItemCode,
                                null, null, null, null));
            }
            built.add(new NarrationActivity.Config.ContentItem(contentItemId, List.copyOf(imageCodes), rubricItemCode));
        }
        return new NarrationActivity(activityId, NarrationActivity.TYPE,
                new NarrationActivity.Config(audioCode, List.copyOf(built)));
    }

    private void requireObject(JsonNode node, String path) {
        if (node == null || !node.isObject()) {
            throw configError(path, "此处必须是对象");
        }
    }

    private void requireExactKeys(JsonNode object, Set<String> expected, String path) {
        requireExactKeys(object, expected, Set.of(), path);
    }

    private void requireExactKeys(JsonNode object, Set<String> required, Set<String> optional, String path) {
        for (String field : object.propertyNames()) {
            if (!required.contains(field) && !optional.contains(field)) {
                throw configError(path + "/" + field, "字段不在契约中");
            }
        }
        for (String field : required) {
            if (object.get(field) == null || object.get(field).isMissingNode()) {
                throw configError(path + "/" + field, "缺少必填字段");
            }
        }
    }

    private String requireCode(JsonNode root, String field) {
        String value = requireNonBlankText(root, field, "");
        if (!value.matches(CODE_PATTERN) || value.length() > CODE_MAX) {
            throw configError("/" + field, "编号只能含字母、数字、下划线与连字符");
        }
        return value;
    }

    private String requireVersion(JsonNode root, String field) {
        String value = requireNonBlankText(root, field, "");
        if (!value.matches(VERSION_PATTERN) || value.length() > VERSION_MAX) {
            throw configError("/" + field, "版本标签以字母或数字开头，只能含字母、数字与 . _ -");
        }
        return value;
    }

    private String requireName(JsonNode root) {
        String value = requireNonBlankText(root, "name", "");
        if (value.length() > NAME_MAX) {
            throw configError("/name", "名称过长");
        }
        return value;
    }

    private String requireNonBlankText(JsonNode object, String field, String path) {
        JsonNode node = object.get(field);
        String fieldPath = path + "/" + field;
        if (node == null || !node.isTextual() || node.asString().isBlank()) {
            throw configError(fieldPath, "必须是字符串且不能为空");
        }
        return node.asString();
    }

    private String requireText(JsonNode object, String field, String path) {
        JsonNode node = object.get(field);
        if (node == null || !node.isTextual()) {
            throw configError(path + "/" + field, "必须是字符串，允许为空");
        }
        return node.asString();
    }

    private BusinessException configError(String fieldPath, String message) {
        return new BusinessException(ErrorCode.INVALID_ACTIVITY_CONFIG, message, ApiErrorDetails.atField(fieldPath));
    }

}
