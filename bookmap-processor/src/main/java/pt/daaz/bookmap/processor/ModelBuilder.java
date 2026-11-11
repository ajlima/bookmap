package pt.daaz.bookmap.processor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import pt.daaz.bookmap.annotation.Direction;
import pt.daaz.bookmap.annotation.Usage;
import pt.daaz.bookmap.codec.BookWriter;
import pt.daaz.bookmap.processor.model.CollectionModel;
import pt.daaz.bookmap.processor.model.FieldModel;
import pt.daaz.bookmap.processor.model.FieldModel.JavaKind;
import pt.daaz.bookmap.processor.model.ItemModel;
import pt.daaz.bookmap.processor.model.MemberModel;
import pt.daaz.bookmap.processor.model.OperationModel;

/**
 * Builds the {@link OperationModel} of an operation class and checks the mapping rules.
 * The builder reports each problem as a compile error on the related element.
 *
 * <p>A message about a member starts with the member descriptor
 * {@code "<ownerSimpleName>.<name> (order <n>)"}. A message about the operation class starts
 * with {@code "<SimpleClassName>: "}.
 */
public final class ModelBuilder {

    static final String BOOK_OPERATION = "pt.daaz.bookmap.annotation.BookOperation";
    private static final String BOOK_FIELD = "pt.daaz.bookmap.annotation.BookField";
    private static final String BOOK_COLLECTION = "pt.daaz.bookmap.annotation.BookCollection";
    private static final String NO_CONVERTER = "pt.daaz.bookmap.convert.NoConverter";
    private static final String FIELD_CONVERTER = "pt.daaz.bookmap.convert.FieldConverter";
    private static final String CONTINUABLE = "pt.daaz.bookmap.Continuable";
    private static final String VALIDATABLE = "pt.daaz.bookmap.Validatable";
    private static final String DEFAULT_PATTERN = "yyyyMMdd";
    private static final int MAX_COMP_DIGITS = 18;

    private static final Map<String, JavaKind> KINDS = Map.of(
        "java.lang.String", JavaKind.STRING,
        "int", JavaKind.INT,
        "java.lang.Integer", JavaKind.INTEGER,
        "long", JavaKind.LONG,
        "java.lang.Long", JavaKind.LONG_BOXED,
        "java.math.BigDecimal", JavaKind.BIG_DECIMAL,
        "java.time.LocalDate", JavaKind.LOCAL_DATE,
        "java.time.LocalTime", JavaKind.LOCAL_TIME,
        "java.time.LocalDateTime", JavaKind.LOCAL_DATE_TIME);

    private static final Set<JavaKind> DISPLAY_ONLY = EnumSet.of(JavaKind.STRING,
        JavaKind.LOCAL_DATE, JavaKind.LOCAL_TIME, JavaKind.LOCAL_DATE_TIME);

    private static final Map<JavaKind, Integer> MAX_DIGITS = Map.of(
        JavaKind.INT, 9, JavaKind.INTEGER, 9, JavaKind.LONG, 18, JavaKind.LONG_BOXED, 18);

    private static final Set<JavaKind> COUNT_KINDS = EnumSet.of(JavaKind.INT, JavaKind.INTEGER,
        JavaKind.LONG, JavaKind.LONG_BOXED);

    private final Elements elements;
    private final Types types;
    private final Messager messager;
    private final AccessorResolver accessors;
    private boolean deferred;

    /**
     * Makes a builder.
     *
     * @param env the processing environment
     */
    public ModelBuilder(ProcessingEnvironment env) {
        this.elements = env.getElementUtils();
        this.types = env.getTypeUtils();
        this.messager = env.getMessager();
        this.accessors = new AccessorResolver(elements);
    }

    /**
     * Builds the model of an operation class.
     *
     * <p>If a field type or a superclass is not available yet (another generator makes it later),
     * or if only getters or setters are missing, and {@code lastRound} is {@code false}, the
     * method reports nothing and returns empty. Then {@link #deferred()} returns {@code true}. In all other
     * cases the method reports the problems. It reports missing getters and setters only in the
     * last round.
     *
     * @param type the class with {@code @BookOperation}
     * @param lastRound {@code true} in the last processing round
     * @return the model, or empty if the class has problems or must wait for a later round
     */
    public Optional<OperationModel> build(TypeElement type, boolean lastRound) {
        Run run = new Run(type);
        OperationModel model = run.build();
        deferred = !lastRound
            && (run.unresolved || run.errors.isEmpty() && !run.accessorErrors.isEmpty());
        if (deferred) {
            return Optional.empty();
        }
        run.errors.forEach(this::report);
        if (lastRound) {
            // Before the last round, another generator can still add the accessors.
            run.accessorErrors.forEach(this::report);
        }
        return run.errors.isEmpty() && run.accessorErrors.isEmpty()
            ? Optional.of(model) : Optional.empty();
    }

    /**
     * Tells if the last call of {@link #build} deferred the class to a later round.
     *
     * @return {@code true} if the class must be built again in the next round
     */
    boolean deferred() {
        return deferred;
    }

    private void report(Problem problem) {
        messager.printMessage(Diagnostic.Kind.ERROR, problem.message(), problem.element());
    }

    private record Problem(Element element, String message) {
    }

    /** One field with {@code @BookField} or {@code @BookCollection}. */
    private record Annotated(VariableElement field, boolean collection, int order,
                             Map<String, Object> values) {

        String owner() {
            return field.getEnclosingElement().getSimpleName().toString();
        }

        String name() {
            return field.getSimpleName().toString();
        }

        String descriptor() {
            return MemberModel.descriptor(owner(), name(), order);
        }
    }

    /** The state of one call of {@link #build}. */
    private final class Run {

        private final TypeElement type;
        private final boolean continuable;
        private final List<Problem> errors = new ArrayList<>();
        private final List<Problem> accessorErrors = new ArrayList<>();
        private boolean unresolved;

        Run(TypeElement type) {
            this.type = type;
            this.continuable = implementsInterface(type, CONTINUABLE);
        }

        OperationModel build() {
            checkOperationClass();
            String operationName = operationName();
            List<Annotated> annotated = annotatedFields(type);
            checkDuplicateOrders(annotated);
            Map<String, Annotated> byName = new HashMap<>();
            Map<String, FieldModel> fields = new HashMap<>();
            List<MemberModel> members = new ArrayList<>();
            for (Annotated member : annotated) {
                byName.put(member.name(), member);
                if (!member.collection()) {
                    FieldModel field = field(type, member, null);
                    if (field != null) {
                        fields.put(member.name(), field);
                        members.add(field);
                    }
                }
            }
            Map<String, String> countUsers = new HashMap<>();
            for (Annotated member : annotated) {
                if (member.collection()) {
                    CollectionModel collection = collection(member, byName, fields, countUsers);
                    if (collection != null) {
                        members.add(collection);
                    }
                }
            }
            members.sort(Comparator.comparingInt(MemberModel::order));
            return new OperationModel(type, operationName, "", List.copyOf(members), continuable,
                implementsInterface(type, VALIDATABLE));
        }

        // ---- operation class ----

        private void checkOperationClass() {
            if (type.getKind() == ElementKind.RECORD) {
                classError("operation class must not be a record");
                return;
            }
            if (type.getKind() != ElementKind.CLASS) {
                classError("@BookOperation must be on a class");
                return;
            }
            Set<Modifier> modifiers = type.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC)) {
                classError("operation class must be public");
            }
            if (modifiers.contains(Modifier.ABSTRACT)) {
                classError("operation class must not be abstract");
            }
            if (!type.getTypeParameters().isEmpty()) {
                classError("operation class must not be generic");
            }
            if (!topLevelOrStatic(type)) {
                classError("operation class must be top-level or static");
            }
            if (!hasPublicNoArgConstructor(type)) {
                classError("operation class must have a public no-argument constructor");
            }
        }

        private String operationName() {
            AnnotationMirror mirror = Mirrors.find(type, BOOK_OPERATION).orElseThrow();
            String value = (String) Mirrors.explicitValues(mirror).getOrDefault("value", "");
            if (value.isEmpty()) {
                return type.getSimpleName().toString();
            }
            if (value.isBlank()) {
                classError("operation name must not be blank");
            }
            return value;
        }

        private void classError(String message) {
            errors.add(new Problem(type, type.getSimpleName() + ": " + message));
        }

        // ---- annotated fields ----

        /** Gives the annotated fields of a class and its superclasses, superclass fields first. */
        private List<Annotated> annotatedFields(TypeElement owner) {
            List<TypeElement> hierarchy = new ArrayList<>();
            for (TypeElement current = owner; current != null; current = superclass(current)) {
                hierarchy.add(0, current);
            }
            List<Annotated> result = new ArrayList<>();
            for (TypeElement current : hierarchy) {
                for (VariableElement field : ElementFilter.fieldsIn(current.getEnclosedElements())) {
                    Optional<AnnotationMirror> bookField = Mirrors.find(field, BOOK_FIELD);
                    Optional<AnnotationMirror> bookCollection = Mirrors.find(field,
                        BOOK_COLLECTION);
                    if (bookField.isEmpty() && bookCollection.isEmpty()) {
                        continue;
                    }
                    Map<String, Object> values =
                        Mirrors.explicitValues(bookField.or(() -> bookCollection).orElseThrow());
                    Annotated member = new Annotated(field, bookField.isEmpty(),
                        (Integer) values.get("order"), values);
                    if (field.getModifiers().contains(Modifier.STATIC)) {
                        error(member, "an annotated field must not be static");
                    } else if (bookField.isPresent() && bookCollection.isPresent()) {
                        error(member, "a field must not have both @BookField and @BookCollection");
                    } else {
                        unresolved |= isUnresolved(field.asType());
                        result.add(member);
                    }
                }
            }
            return result;
        }

        private TypeElement superclass(TypeElement current) {
            TypeMirror superclass = current.getSuperclass();
            unresolved |= superclass.getKind() == TypeKind.ERROR;
            return superclass.getKind() == TypeKind.DECLARED
                ? (TypeElement) types.asElement(superclass) : null;
        }

        private static boolean isUnresolved(TypeMirror type) {
            return type.getKind() == TypeKind.ERROR
                || type instanceof DeclaredType declared && declared.getTypeArguments().stream()
                    .anyMatch(argument -> argument.getKind() == TypeKind.ERROR);
        }

        private void checkDuplicateOrders(List<Annotated> members) {
            Map<Integer, Annotated> seen = new HashMap<>();
            for (Annotated member : members) {
                Annotated previous = seen.putIfAbsent(member.order(), member);
                if (previous != null) {
                    error(member, "duplicate order, also used by " + previous.owner() + "."
                        + previous.name());
                }
            }
        }

        private void error(Annotated member, String message) {
            errors.add(new Problem(member.field(), member.descriptor() + ": " + message));
        }

        // ---- fields ----

        /**
         * Builds a field model.
         *
         * @param owner the class that the generated code uses for the accessors
         * @param member the annotated field
         * @param itemDirection the collection direction for an item field, or null
         * @return the model, or null if the field has problems
         */
        private FieldModel field(TypeElement owner, Annotated member, Direction itemDirection) {
            int before = errors.size();
            Map<String, Object> values = member.values();
            Direction direction;
            if (itemDirection == null) {
                direction = Direction.valueOf(
                    (String) values.getOrDefault("direction", Direction.INPUT_OUTPUT.name()));
            } else {
                if (values.containsKey("direction")) {
                    error(member, "direction must not be set on an item field");
                }
                direction = itemDirection;
            }
            Usage usage = Usage.valueOf(
                (String) values.getOrDefault("usage", Usage.DISPLAY.name()));
            int scale = (Integer) values.getOrDefault("scale", 0);
            boolean signed = (Boolean) values.getOrDefault("signed", false);
            String pattern = (String) values.getOrDefault("pattern", DEFAULT_PATTERN);
            TypeMirror fieldType = member.field().asType();
            TypeElement converter = converter(values);
            JavaKind kind;
            if (converter == null) {
                kind = KINDS.get(fieldType.toString());
                if (kind == null) {
                    error(member, "type " + fieldType + " is not supported without a converter");
                }
            } else {
                kind = JavaKind.CONVERTED;
                checkConverter(member, converter, fieldType);
            }
            Integer length = (Integer) values.get("length");
            if (length == null) {
                error(member, "length is missing");
            } else if (length < 1) {
                error(member, "length must be at least 1");
            }
            if (scale < 0) {
                error(member, "scale must not be negative");
            }
            if (errors.size() == before) {
                checkTypeAndUsage(member, kind, fieldType, usage, length, scale);
            }
            Optional<String> getter = getter(owner, member);
            Optional<String> setter = setter(owner, member);
            if (errors.size() != before || getter.isEmpty() || setter.isEmpty()) {
                return null;
            }
            return new FieldModel(member.owner(), member.name(), member.order(), length,
                direction, usage, scale, signed, pattern, kind, fieldType, getter.get(),
                setter.get(), converter, BookWriter.byteLength(usage, length));
        }

        private void checkTypeAndUsage(Annotated member, JavaKind kind, TypeMirror fieldType,
                                       Usage usage, int length, int scale) {
            String typeName = fieldType.toString();
            String simpleName = typeName.substring(typeName.lastIndexOf('.') + 1);
            if (DISPLAY_ONLY.contains(kind) && usage != Usage.DISPLAY) {
                error(member, usage + " is not permitted for " + simpleName);
            }
            Integer maxDigits = MAX_DIGITS.get(kind);
            if (maxDigits != null) {
                if (scale != 0) {
                    error(member, "scale must be 0 for " + simpleName);
                }
                if (length > maxDigits) {
                    error(member, "length " + length + " is more than " + maxDigits
                        + " digits for " + simpleName);
                }
            }
            if (kind == JavaKind.BIG_DECIMAL && scale > length) {
                error(member, "scale must not be more than length");
            }
            if (usage == Usage.COMP && length > MAX_COMP_DIGITS) {
                error(member, "COMP length must not be more than " + MAX_COMP_DIGITS);
            }
        }

        private TypeElement converter(Map<String, Object> values) {
            Object value = values.get("converter");
            if (value == null) {
                return null;
            }
            TypeElement converter = (TypeElement) types.asElement((TypeMirror) value);
            return converter.getQualifiedName().contentEquals(NO_CONVERTER) ? null : converter;
        }

        private void checkConverter(Annotated member, TypeElement converter,
                                    TypeMirror fieldType) {
            if (!instantiable(converter)) {
                error(member, "converter " + converter.getQualifiedName()
                    + " must be a public class with a public no-argument constructor");
            }
            TypeMirror javaType = converterJavaType(converter.asType());
            TypeMirror boxed = fieldType.getKind().isPrimitive()
                ? types.boxedClass((PrimitiveType) fieldType).asType() : fieldType;
            if (!types.isSameType(javaType, boxed)) {
                error(member, "converter type " + javaType + " does not agree with field type "
                    + boxed);
            }
        }

        /** Finds {@code J} of {@code FieldConverter<J>} in the supertypes of a converter. */
        private TypeMirror converterJavaType(TypeMirror type) {
            for (TypeMirror supertype : types.directSupertypes(type)) {
                DeclaredType declared = (DeclaredType) supertype;
                if (((TypeElement) declared.asElement()).getQualifiedName()
                    .contentEquals(FIELD_CONVERTER)) {
                    return declared.getTypeArguments().isEmpty()
                        ? elements.getTypeElement("java.lang.Object").asType()
                        : declared.getTypeArguments().get(0);
                }
                TypeMirror found = converterJavaType(supertype);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        private Optional<String> getter(TypeElement owner, Annotated member) {
            Optional<String> getter = accessors.getter(owner, member.field());
            if (getter.isEmpty()) {
                accessorErrors.add(new Problem(member.field(), member.descriptor()
                    + ": missing getter get" + capitalized(member)));
            }
            return getter;
        }

        private Optional<String> setter(TypeElement owner, Annotated member) {
            Optional<String> setter = accessors.setter(owner, member.field());
            if (setter.isEmpty()) {
                accessorErrors.add(new Problem(member.field(), member.descriptor()
                    + ": missing setter set" + capitalized(member)));
            }
            return setter;
        }

        private static String capitalized(Annotated member) {
            String name = member.name();
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        // ---- collections ----

        private CollectionModel collection(Annotated member, Map<String, Annotated> byName,
                                           Map<String, FieldModel> fields,
                                           Map<String, String> countUsers) {
            int before = errors.size();
            Map<String, Object> values = member.values();
            Direction direction = Direction.valueOf(
                (String) values.getOrDefault("direction", Direction.OUTPUT.name()));
            int maxOccurs = (Integer) values.get("maxOccurs");
            if (maxOccurs < 1) {
                error(member, "maxOccurs must be at least 1");
            }
            if (continuable && direction == Direction.INPUT_OUTPUT) {
                error(member, "an INPUT_OUTPUT collection is not permitted in a Continuable class");
            }
            ItemModel item = item(member, direction);
            FieldModel count = countField(member, direction, byName, fields, countUsers);
            Optional<String> getter = getter(type, member);
            Optional<String> setter = setter(type, member);
            if (errors.size() != before || item == null || count == null || getter.isEmpty()
                || setter.isEmpty()) {
                return null;
            }
            return new CollectionModel(member.owner(), member.name(), member.order(), direction,
                count, maxOccurs, item, getter.get(), setter.get());
        }

        private ItemModel item(Annotated collection, Direction direction) {
            TypeElement itemType = listItem(collection.field().asType());
            if (itemType == null) {
                error(collection, "a collection must have the type List<X>");
                return null;
            }
            int before = errors.size();
            if (!instantiable(itemType)) {
                error(collection, "item class " + itemType.getQualifiedName()
                    + " must be a public class with a public no-argument constructor");
            }
            List<Annotated> annotated = annotatedFields(itemType);
            if (annotated.stream().anyMatch(Annotated::collection)) {
                error(collection, "item class " + itemType.getQualifiedName()
                    + " must not contain a @BookCollection (nested collection)");
                return null;
            }
            checkDuplicateOrders(annotated);
            List<FieldModel> fields = new ArrayList<>();
            for (Annotated member : annotated) {
                FieldModel field = field(itemType, member, direction);
                if (field != null) {
                    fields.add(field);
                }
            }
            if (errors.size() != before || fields.size() != annotated.size()) {
                return null;
            }
            fields.sort(Comparator.comparingInt(FieldModel::order));
            int byteLength = fields.stream().mapToInt(FieldModel::byteLength).sum();
            return new ItemModel(itemType, List.copyOf(fields), byteLength);
        }

        private TypeElement listItem(TypeMirror fieldType) {
            TypeMirror list = types.erasure(elements.getTypeElement("java.util.List").asType());
            if (!types.isSameType(types.erasure(fieldType), list)) {
                return null;
            }
            List<? extends TypeMirror> arguments = ((DeclaredType) fieldType).getTypeArguments();
            if (arguments.size() != 1 || arguments.get(0).getKind() != TypeKind.DECLARED) {
                return null;
            }
            return (TypeElement) types.asElement(arguments.get(0));
        }

        private FieldModel countField(Annotated collection, Direction direction,
                                      Map<String, Annotated> byName,
                                      Map<String, FieldModel> fields,
                                      Map<String, String> countUsers) {
            String name = (String) collection.values().getOrDefault("dependsOn", "");
            if (name.isEmpty()) {
                error(collection, "dependsOn is missing");
                return null;
            }
            Annotated target = byName.get(name);
            String prefix = "dependsOn field '" + name + "' ";
            if (target == null) {
                error(collection, prefix + "does not exist");
                return null;
            }
            FieldModel count = fields.get(name);
            if (target.collection() || count != null && !COUNT_KINDS.contains(count.kind())) {
                error(collection, prefix + "must be int, Integer, long, or Long");
                return null;
            }
            if (count == null) {
                // The count field has its own errors.
                return null;
            }
            int before = errors.size();
            if (count.order() >= collection.order()) {
                error(collection, prefix + "must come before the collection");
            }
            if (direction.isInput() && !count.direction().isInput()
                || direction.isOutput() && !count.direction().isOutput()) {
                error(collection, prefix + "has direction " + count.direction()
                    + ", which does not include " + direction);
            }
            String previous = countUsers.putIfAbsent(name, collection.name());
            if (previous != null) {
                error(collection, prefix + "is already used by collection '" + previous + "'");
            }
            return errors.size() == before ? count : null;
        }

        // ---- helpers ----

        private boolean implementsInterface(TypeElement element, String interfaceName) {
            return types.isAssignable(element.asType(),
                types.erasure(elements.getTypeElement(interfaceName).asType()));
        }
    }

    private static boolean instantiable(TypeElement element) {
        return element.getKind() == ElementKind.CLASS
            && element.getModifiers().contains(Modifier.PUBLIC)
            && !element.getModifiers().contains(Modifier.ABSTRACT)
            && topLevelOrStatic(element)
            && hasPublicNoArgConstructor(element);
    }

    private static boolean topLevelOrStatic(TypeElement element) {
        return element.getNestingKind() != NestingKind.MEMBER
            || element.getModifiers().contains(Modifier.STATIC);
    }

    private static boolean hasPublicNoArgConstructor(TypeElement element) {
        return ElementFilter.constructorsIn(element.getEnclosedElements()).stream()
            .anyMatch(constructor -> constructor.getParameters().isEmpty()
                && constructor.getModifiers().contains(Modifier.PUBLIC));
    }
}
