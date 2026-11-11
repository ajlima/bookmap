package pt.dazz.bookmap.processor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import pt.dazz.bookmap.annotation.Direction;
import pt.dazz.bookmap.processor.model.CollectionModel;
import pt.dazz.bookmap.processor.model.FieldModel;
import pt.dazz.bookmap.processor.model.FieldModel.JavaKind;
import pt.dazz.bookmap.processor.model.ItemModel;
import pt.dazz.bookmap.processor.model.MemberModel;
import pt.dazz.bookmap.processor.model.OperationModel;
import pt.dazz.bookmap.processor.model.SubTypesModel;
import pt.dazz.bookmap.processor.model.VariantModel;

/**
 * Builds the {@link OperationModel} of an operation class and checks the mapping rules.
 * The builder reports each problem as a compile error on the related element.
 * A {@link FieldResolver} resolves and checks each field.
 *
 * <p>A message about a member starts with the member descriptor
 * {@code "<ownerSimpleName>.<name> (order <n>)"}. A message about the operation class starts
 * with {@code "<SimpleClassName>: "}.
 */
public final class ModelBuilder {

    static final String BOOK_OPERATION = "pt.dazz.bookmap.annotation.BookOperation";
    private static final String BOOK_FIELD = "pt.dazz.bookmap.annotation.BookField";
    private static final String BOOK_COLLECTION = "pt.dazz.bookmap.annotation.BookCollection";
    private static final String CONTINUABLE = "pt.dazz.bookmap.Continuable";
    private static final String VALIDATABLE = "pt.dazz.bookmap.Validatable";

    private static final Set<JavaKind> COUNT_KINDS = EnumSet.of(JavaKind.INT, JavaKind.INTEGER,
        JavaKind.LONG, JavaKind.LONG_BOXED);

    private final Elements elements;
    private final Types types;
    private final Messager messager;
    private final DefaultsResolver defaultsResolver;
    private final FieldResolver fieldResolver;
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
        this.defaultsResolver = new DefaultsResolver(elements);
        this.fieldResolver = new FieldResolver(elements, types, defaultsResolver);
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
        Problems problems = run.problems;
        deferred = !lastRound && (problems.unresolved
            || problems.errors.isEmpty() && !problems.accessorErrors.isEmpty());
        if (deferred) {
            return Optional.empty();
        }
        problems.errors.forEach(this::report);
        if (lastRound) {
            // Before the last round, another generator can still add the accessors.
            problems.accessorErrors.forEach(this::report);
        }
        return !problems.unresolved && problems.errors.isEmpty()
            && problems.accessorErrors.isEmpty() ? Optional.of(model) : Optional.empty();
    }

    /**
     * Tells if the last call of {@link #build} deferred the class to a later round.
     *
     * @return {@code true} if the class must be built again in the next round
     */
    boolean deferred() {
        return deferred;
    }

    private void report(Problems.Problem problem) {
        messager.printMessage(Diagnostic.Kind.ERROR, problem.message(), problem.element());
    }

    /** The state of one call of {@link #build}. */
    private final class Run {

        private final TypeElement type;
        /** The package of the operation class and of the generated codec. */
        private final PackageElement codecPackage;
        private final boolean continuable;
        private final Problems problems = new Problems();
        /** The operation class, its superclasses, the item classes, and their superclasses. */
        private final Set<TypeElement> declaringClasses = new LinkedHashSet<>();

        Run(TypeElement type) {
            this.type = type;
            this.codecPackage = elements.getPackageOf(type);
            this.continuable = implementsInterface(type, CONTINUABLE);
        }

        OperationModel build() {
            checkOperationClass();
            String operationName = operationName();
            int before = problems.count();
            List<AnnotatedMember> annotated = annotatedFields(type);
            if (noMembers(annotated, before)) {
                classError("operation class must have at least one @BookField or @BookCollection");
            }
            checkDuplicateOrders(annotated);
            Map<String, AnnotatedMember> byName = new HashMap<>();
            Map<String, FieldModel> fields = new HashMap<>();
            List<MemberModel> members = new ArrayList<>();
            for (AnnotatedMember member : annotated) {
                byName.put(member.name(), member);
                if (!member.collection()) {
                    FieldModel field = fieldResolver.resolve(type, member, null, codecPackage,
                        problems);
                    if (field != null) {
                        fields.put(member.name(), field);
                        members.add(field);
                    }
                }
            }
            Map<String, String> countUsers = new HashMap<>();
            for (AnnotatedMember member : annotated) {
                if (member.collection()) {
                    CollectionModel collection = collection(type, continuable, member, annotated,
                        byName, Set.of(), fields, members, countUsers);
                    if (collection != null) {
                        members.add(collection);
                    }
                }
            }
            members.sort(Comparator.comparingInt(MemberModel::order));
            SubTypesModel subTypes = SubTypesResolver.present(type)
                ? subTypes(type, annotated, members, null) : null;
            String charsetName = charset();
            return new OperationModel(type, operationName, charsetName, List.copyOf(members),
                continuable, implementsInterface(type, VALIDATABLE), subTypes);
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
            // A base class with subtypes can be abstract. Then the caller sends a subtype.
            boolean abstractBase = modifiers.contains(Modifier.ABSTRACT)
                && SubTypesResolver.present(type);
            if (modifiers.contains(Modifier.ABSTRACT) && !abstractBase) {
                classError("operation class must not be abstract");
            }
            if (!type.getTypeParameters().isEmpty()) {
                classError("operation class must not be generic");
            }
            if (!Classes.topLevelOrStatic(type)) {
                classError("operation class must be top-level or static");
            }
            if (!abstractBase && !Classes.hasPublicNoArgConstructor(type, elements)) {
                classError("operation class must have a public no-argument constructor");
            }
        }

        private String operationName() {
            AnnotationMirror mirror = Mirrors.find(type, BOOK_OPERATION).orElseThrow();
            Object explicit = Mirrors.explicitValues(mirror).getOrDefault("value", "");
            if (explicit == Mirrors.UNRESOLVED) {
                problems.unresolved = true;
                return type.getSimpleName().toString();
            }
            String value = (String) explicit;
            if (value.isEmpty()) {
                return type.getSimpleName().toString();
            }
            if (value.isBlank()) {
                classError("operation name must not be blank");
            }
            return value;
        }

        private void classError(String message) {
            problems.classError(type, message);
        }

        /**
         * Gives the charset of the operation: the {@code @BookDefaults} charset of the class or
         * its package. Each other declaring class must not set a different charset. For a class
         * in the package of the operation class, only its own {@code @BookDefaults} counts: the
         * operation class can override the package charset.
         */
        private String charset() {
            for (TypeElement declaring : declaringClasses) {
                problems.unresolved |= defaultsResolver.unresolved(declaring);
            }
            String name = defaultsResolver.forDeclaringClass(type).charset().orElse("");
            if (!name.isEmpty()) {
                DefaultsResolver.charsetProblem(name).ifPresent(this::classError);
            }
            String operationCharset = name.isEmpty() ? "(none)" : "'" + name + "'";
            PackageElement operationPackage = elements.getPackageOf(type);
            for (TypeElement declaring : declaringClasses) {
                Optional<String> charset = elements.getPackageOf(declaring).equals(operationPackage)
                    ? defaultsResolver.classCharset(declaring)
                    : defaultsResolver.forDeclaringClass(declaring).charset();
                charset.filter(other -> !other.equals(name))
                    .ifPresent(other -> classError("charset '" + other + "' of "
                        + declaring.getQualifiedName()
                        + " is different from the operation charset " + operationCharset));
            }
            return name;
        }

        // ---- annotated fields ----

        /** Gives the annotated fields of a class and its superclasses, superclass fields first. */
        private List<AnnotatedMember> annotatedFields(TypeElement owner) {
            return annotatedFields(hierarchy(owner));
        }

        /** Gives a class and its superclasses, the top superclass first. */
        private List<TypeElement> hierarchy(TypeElement owner) {
            List<TypeElement> hierarchy = new ArrayList<>();
            for (TypeElement current = owner; current != null; current = superclass(current)) {
                hierarchy.add(0, current);
            }
            return hierarchy;
        }

        /** Gives the annotated fields of the classes, in the order of the list. */
        private List<AnnotatedMember> annotatedFields(List<TypeElement> hierarchy) {
            declaringClasses.addAll(hierarchy);
            List<AnnotatedMember> result = new ArrayList<>();
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
                    if (values.containsValue(Mirrors.UNRESOLVED)) {
                        // javac reports the unresolved value. Wait for a generator, or stop.
                        problems.unresolved = true;
                        continue;
                    }
                    AnnotatedMember member = new AnnotatedMember(field, bookField.isEmpty(),
                        (Integer) values.get("order"), values);
                    if (field.getModifiers().contains(Modifier.STATIC)) {
                        problems.error(member, "an annotated field must not be static");
                    } else if (bookField.isPresent() && bookCollection.isPresent()) {
                        problems.error(member,
                            "a field must not have both @BookField and @BookCollection");
                    } else {
                        problems.unresolved |= isUnresolved(field.asType());
                        result.add(member);
                    }
                }
            }
            return result;
        }

        private TypeElement superclass(TypeElement current) {
            TypeMirror superclass = current.getSuperclass();
            problems.unresolved |= superclass.getKind() == TypeKind.ERROR;
            return superclass.getKind() == TypeKind.DECLARED
                ? (TypeElement) types.asElement(superclass) : null;
        }

        private static boolean isUnresolved(TypeMirror type) {
            return type.getKind() == TypeKind.ERROR
                || type instanceof DeclaredType declared && declared.getTypeArguments().stream()
                    .anyMatch(argument -> argument.getKind() == TypeKind.ERROR);
        }

        private void checkDuplicateOrders(List<AnnotatedMember> members) {
            Map<Integer, AnnotatedMember> seen = new HashMap<>();
            for (AnnotatedMember member : members) {
                AnnotatedMember previous = seen.putIfAbsent(member.order(), member);
                if (previous != null) {
                    problems.error(member, "duplicate order, also used by " + previous.owner()
                        + "." + previous.name());
                }
            }
        }

        // ---- subtypes ----

        /**
         * Builds the subtypes of an operation class or an item class and checks the rules of
         * {@code @BookSubTypes}.
         *
         * @param base the class with {@code @BookSubTypes}
         * @param baseAnnotated the annotated members of the base class and its superclasses
         * @param baseMembers the resolved members of the base class
         * @param itemDirection the collection direction for an item class, or null
         * @return the subtypes, or null after an error
         */
        private SubTypesModel subTypes(TypeElement base, List<AnnotatedMember> baseAnnotated,
                                       List<? extends MemberModel> baseMembers,
                                       Direction itemDirection) {
            Optional<SubTypesResolver.Declared> read = SubTypesResolver.read(base, problems);
            if (read.isEmpty()) {
                return null;
            }
            SubTypesResolver.Declared declared = read.get();
            int before = problems.count();
            boolean baseAbstract = base.getModifiers().contains(Modifier.ABSTRACT);
            if (declared.entries().isEmpty()) {
                problems.classError(base, "@BookSubTypes must list at least one type");
            }
            List<TypeElement> listed = new ArrayList<>();
            List<String> values = new ArrayList<>();
            List<List<MemberModel>> layouts = new ArrayList<>();
            Set<String> ownNames = new HashSet<>();
            for (SubTypesResolver.Entry entry : declared.entries()) {
                TypeElement subtype = subtype(base, entry.type(), listed);
                if (subtype != null) {
                    listed.add(subtype);
                    values.add(entry.value());
                    layouts.add(subtype.equals(base) ? List.copyOf(baseMembers)
                        : variantLayout(base, subtype, baseAnnotated, baseMembers, itemDirection,
                            ownNames));
                }
            }
            checkSubtypesDoNotExtendEachOther(base, listed);
            AnnotatedMember discriminatorMember = discriminatorMember(base,
                declared.discriminator(), baseAnnotated, ownNames);
            FieldModel discriminator = discriminatorMember == null ? null
                : discriminator(base, discriminatorMember, baseMembers, baseAbstract,
                    itemDirection == null);
            if (discriminator == null || problems.count() != before) {
                return null;
            }
            List<VariantModel> variants = new ArrayList<>();
            Map<String, TypeElement> byValue = new HashMap<>();
            for (int i = 0; i < listed.size(); i++) {
                TypeElement subtype = listed.get(i);
                Optional<String> value = SubTypesResolver.normalize(discriminator,
                    discriminatorMember, values.get(i), problems);
                if (value.isPresent()) {
                    TypeElement previous = byValue.putIfAbsent(value.get(), subtype);
                    if (previous != null) {
                        problems.error(discriminatorMember, "discriminator value '" + value.get()
                            + "' is used by " + previous.getSimpleName() + " and "
                            + subtype.getSimpleName());
                    }
                    variants.add(new VariantModel(subtype, value.get(), layouts.get(i)));
                }
            }
            return problems.count() == before
                ? new SubTypesModel(discriminator, List.copyOf(variants), baseAbstract) : null;
        }

        /**
         * Checks one listed type. It must be an instantiable subclass of the base class, listed
         * once, without {@code @BookOperation}, and without {@code @BookSubTypes} below the base.
         *
         * @return the subtype, or null after an error
         */
        private TypeElement subtype(TypeElement base, TypeMirror mirror,
                                    List<TypeElement> listed) {
            TypeElement subtype = mirror.getKind() == TypeKind.DECLARED
                ? (TypeElement) types.asElement(mirror) : null;
            // Only a class can extend the base class.
            if (subtype == null || !types.isSubtype(types.erasure(mirror),
                types.erasure(base.asType()))) {
                problems.classError(base, mirror + " is not a subclass of "
                    + base.getSimpleName());
                return null;
            }
            if (!Classes.instantiable(subtype, elements)
                || !subtype.getTypeParameters().isEmpty()) {
                problems.classError(base, "type " + subtype.getSimpleName()
                    + " must be public, not abstract, not generic, and have a public"
                    + " no-argument constructor");
                return null;
            }
            if (listed.contains(subtype)) {
                problems.classError(base, "type " + subtype.getSimpleName()
                    + " is listed more than once");
                return null;
            }
            int before = problems.count();
            if (!subtype.equals(base) && Mirrors.find(subtype, BOOK_OPERATION).isPresent()) {
                problems.classError(subtype, "a subtype must not have @BookOperation");
            }
            for (TypeElement current : below(base, subtype)) {
                if (SubTypesResolver.present(current)) {
                    problems.classError(current, "subtypes in a subtype are not supported");
                }
            }
            return problems.count() == before ? subtype : null;
        }

        /** Gives the classes between the base class (excluded) and a subtype (included). */
        private List<TypeElement> below(TypeElement base, TypeElement subtype) {
            List<TypeElement> hierarchy = hierarchy(subtype);
            return hierarchy.subList(hierarchy(base).size(), hierarchy.size());
        }

        private void checkSubtypesDoNotExtendEachOther(TypeElement base,
                                                       List<TypeElement> listed) {
            for (TypeElement subtype : listed) {
                for (TypeElement other : listed) {
                    if (!other.equals(base) && !other.equals(subtype)
                        && types.isSubtype(subtype.asType(), other.asType())) {
                        problems.classError(subtype, "a subtype must not extend another subtype");
                    }
                }
            }
        }

        /**
         * Builds the full layout of a subtype: the base members, then the members of the classes
         * below the base class.
         *
         * @param itemDirection the collection direction for an item class, or null
         * @param ownNames receives the names of the members below the base class
         */
        private List<MemberModel> variantLayout(TypeElement base, TypeElement subtype,
                                                List<AnnotatedMember> baseAnnotated,
                                                List<? extends MemberModel> baseMembers,
                                                Direction itemDirection,
                                                Set<String> ownNames) {
            List<AnnotatedMember> own = annotatedFields(below(base, subtype));
            int lastBaseOrder = baseAnnotated.stream().mapToInt(AnnotatedMember::order).max()
                .orElse(Integer.MIN_VALUE);
            for (AnnotatedMember member : own) {
                ownNames.add(member.name());
                if (member.order() <= lastBaseOrder) {
                    problems.error(member, "a subtype member must come after all base members");
                }
            }
            checkDuplicateOrders(own);
            Set<String> baseNames = new HashSet<>();
            baseAnnotated.forEach(member -> baseNames.add(member.name()));
            Map<String, AnnotatedMember> byName = new HashMap<>();
            Map<String, FieldModel> fields = new HashMap<>();
            List<MemberModel> members = new ArrayList<>();
            for (AnnotatedMember member : own) {
                byName.put(member.name(), member);
                if (member.collection() && itemDirection != null) {
                    problems.error(member, "an item class must not contain a @BookCollection"
                        + " (nested collection)");
                } else if (!member.collection()) {
                    FieldModel field = fieldResolver.resolve(subtype, member, itemDirection,
                        codecPackage, problems);
                    if (field != null) {
                        fields.put(member.name(), field);
                        members.add(field);
                    }
                }
            }
            boolean subtypeContinuable = implementsInterface(subtype, CONTINUABLE);
            Map<String, String> countUsers = new HashMap<>();
            for (AnnotatedMember member : own) {
                if (member.collection() && itemDirection == null) {
                    CollectionModel collection = collection(subtype, subtypeContinuable, member,
                        own, byName, baseNames, fields, members, countUsers);
                    if (collection != null) {
                        members.add(collection);
                    }
                }
            }
            List<MemberModel> layout = new ArrayList<>(baseMembers);
            layout.addAll(members);
            layout.sort(Comparator.comparingInt(MemberModel::order));
            return List.copyOf(layout);
        }

        /** Finds the annotated discriminator in the base members. */
        private AnnotatedMember discriminatorMember(TypeElement base, String name,
                                                    List<AnnotatedMember> baseAnnotated,
                                                    Set<String> ownNames) {
            Optional<AnnotatedMember> member = baseAnnotated.stream()
                .filter(candidate -> candidate.name().equals(name)).findFirst();
            if (member.isEmpty()) {
                problems.classError(base, "discriminator field '" + name + "' "
                    + (ownNames.contains(name)
                    ? "must be in the base class or its superclasses" : "does not exist"));
                return null;
            }
            return member.get();
        }

        /**
         * Checks the discriminator: its type, its converter, its direction, and its position.
         * The direction rules apply only to an operation class.
         *
         * @return the discriminator field, or null after an error
         */
        private FieldModel discriminator(TypeElement base, AnnotatedMember member,
                                         List<? extends MemberModel> baseMembers,
                                         boolean baseAbstract, boolean operation) {
            Optional<FieldModel> resolved = baseMembers.stream()
                .filter(candidate -> candidate.name().equals(member.name()))
                .filter(FieldModel.class::isInstance).map(FieldModel.class::cast).findFirst();
            if (member.collection()) {
                problems.error(member, "discriminator must be String, int, Integer, long, or Long");
                return null;
            }
            if (resolved.isEmpty()) {
                // The field has its own errors.
                return null;
            }
            FieldModel field = resolved.get();
            int before = problems.count();
            if (field.kind() == JavaKind.CONVERTED) {
                problems.error(member, "discriminator must not have a converter");
            } else if (field.kind() != JavaKind.STRING
                && !SubTypesResolver.NUMERIC_KINDS.contains(field.kind())) {
                problems.error(member, "discriminator must be String, int, Integer, long, or Long");
            }
            if (operation && !field.direction().isOutput()) {
                problems.error(member,
                    "discriminator must have direction OUTPUT or INPUT_OUTPUT");
            } else if (operation && baseAbstract && !field.direction().isInput()) {
                problems.classError(base,
                    "an abstract base class needs an INPUT_OUTPUT discriminator");
            }
            if (baseMembers.stream().anyMatch(candidate -> candidate instanceof CollectionModel
                && candidate.order() < field.order())) {
                problems.error(member, "discriminator must not come after a collection");
            }
            return problems.count() == before ? field : null;
        }

        // ---- collections ----

        /**
         * Builds a collection model.
         *
         * @param owner the class that the generated code uses for the accessors
         * @param continuable {@code true} if the owner implements {@code Continuable}
         * @param outside the names of members that the layout cannot use as a count field
         */
        private CollectionModel collection(TypeElement owner, boolean continuable,
                                           AnnotatedMember member,
                                           List<AnnotatedMember> annotated,
                                           Map<String, AnnotatedMember> byName,
                                           Set<String> outside,
                                           Map<String, FieldModel> fields,
                                           List<MemberModel> members,
                                           Map<String, String> countUsers) {
            int before = problems.count();
            Map<String, Object> values = member.values();
            Direction direction = Direction.valueOf(
                (String) values.getOrDefault("direction", Direction.OUTPUT.name()));
            int maxOccurs = (Integer) values.get("maxOccurs");
            if (maxOccurs < 1) {
                problems.error(member, "maxOccurs must be at least 1");
            }
            if (continuable && direction == Direction.INPUT_OUTPUT) {
                problems.error(member,
                    "an INPUT_OUTPUT collection is not permitted in a Continuable class");
            }
            ItemModel item = item(member, direction);
            FieldModel count = countField(member, direction, maxOccurs, annotated, byName, outside,
                fields, members, countUsers);
            Optional<String> getter = fieldResolver.getter(owner, member, codecPackage, problems);
            Optional<String> setter = fieldResolver.setter(owner, member, codecPackage, problems);
            if (problems.count() != before || item == null || count == null || getter.isEmpty()
                || setter.isEmpty()) {
                return null;
            }
            return new CollectionModel(member.owner(), member.name(), member.order(), direction,
                count, maxOccurs, item, getter.get(), setter.get());
        }

        private ItemModel item(AnnotatedMember collection, Direction direction) {
            TypeElement itemType = listItem(collection.field().asType());
            if (itemType == null) {
                problems.error(collection, "a collection must have the type List<X>");
                return null;
            }
            int before = problems.count();
            // An abstract item class with subtypes needs only to be public: the codec never
            // creates it.
            boolean abstractBase = itemType.getModifiers().contains(Modifier.ABSTRACT)
                && SubTypesResolver.present(itemType);
            if (abstractBase ? !itemType.getModifiers().contains(Modifier.PUBLIC)
                : !Classes.instantiable(itemType, elements)) {
                problems.error(collection, "item class " + itemType.getQualifiedName()
                    + " must be a public class with a public no-argument constructor");
            }
            if (!itemType.getTypeParameters().isEmpty()) {
                problems.error(collection, "item class " + itemType.getQualifiedName()
                    + " must not have type parameters");
            }
            int beforeFields = problems.count();
            List<AnnotatedMember> annotated = annotatedFields(itemType);
            if (noMembers(annotated, beforeFields)) {
                problems.error(collection, "item class " + itemType.getQualifiedName()
                    + " must have at least one @BookField");
                return null;
            }
            if (annotated.stream().anyMatch(AnnotatedMember::collection)) {
                problems.error(collection, "item class " + itemType.getQualifiedName()
                    + " must not contain a @BookCollection (nested collection)");
                return null;
            }
            checkDuplicateOrders(annotated);
            List<FieldModel> fields = new ArrayList<>();
            for (AnnotatedMember member : annotated) {
                FieldModel field = fieldResolver.resolve(itemType, member, direction,
                    codecPackage, problems);
                if (field != null) {
                    fields.add(field);
                }
            }
            if (problems.count() != before || fields.size() != annotated.size()) {
                return null;
            }
            fields.sort(Comparator.comparingInt(FieldModel::order));
            int byteLength = fields.stream().mapToInt(FieldModel::byteLength).sum();
            if (!SubTypesResolver.present(itemType)) {
                return new ItemModel(itemType, List.copyOf(fields), byteLength, null);
            }
            SubTypesModel subTypes = subTypes(itemType, annotated, fields, direction);
            if (subTypes == null) {
                return null;
            }
            // Each entry has the length of the longest layout.
            int longest = subTypes.baseAbstract() ? 0 : byteLength;
            for (VariantModel variant : subTypes.variants()) {
                longest = Math.max(longest, variant.members().stream()
                    .mapToInt(member -> ((FieldModel) member).byteLength()).sum());
            }
            return new ItemModel(itemType, List.copyOf(fields), longest, subTypes);
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

        /**
         * Finds the count field of a collection: the {@code dependsOn} field, else the member
         * immediately before the collection. A count field without an explicit direction gets
         * the direction of the collection. The method replaces it in {@code fields} and
         * {@code members}. The digits of the count field must hold {@code maxOccurs}.
         */
        private FieldModel countField(AnnotatedMember collection, Direction direction,
                                      int maxOccurs, List<AnnotatedMember> annotated,
                                      Map<String, AnnotatedMember> byName,
                                      Set<String> outside,
                                      Map<String, FieldModel> fields,
                                      List<MemberModel> members,
                                      Map<String, String> countUsers) {
            String name = (String) collection.values().getOrDefault("dependsOn", "");
            if (name.isEmpty()) {
                Optional<AnnotatedMember> previous = annotated.stream()
                    .filter(member -> member.order() < collection.order())
                    .max(Comparator.comparingInt(AnnotatedMember::order));
                if (previous.isEmpty()) {
                    problems.error(collection,
                        "dependsOn is missing and no member comes before the collection");
                    return null;
                }
                name = previous.get().name();
            }
            AnnotatedMember target = byName.get(name);
            String prefix = "dependsOn field '" + name + "' ";
            if (target == null && outside.contains(name)) {
                problems.error(collection, prefix + "must be in the subtype layout");
                return null;
            }
            if (target == null) {
                problems.error(collection, prefix + "does not exist");
                return null;
            }
            FieldModel count = fields.get(name);
            if (target.collection() || count != null && !COUNT_KINDS.contains(count.kind())) {
                problems.error(collection, prefix + "must be int, Integer, long, or Long");
                return null;
            }
            if (count == null) {
                // The count field has its own errors.
                return null;
            }
            int before = problems.count();
            if (count.order() >= collection.order()) {
                problems.error(collection, prefix + "must come before the collection");
            }
            BigInteger largestCount = BigInteger.TEN.pow(count.length()).subtract(BigInteger.ONE);
            if (largestCount.compareTo(BigInteger.valueOf(maxOccurs)) < 0) {
                problems.error(collection, prefix + "has " + count.length()
                    + " digits and cannot hold maxOccurs " + maxOccurs);
            }
            if (!target.values().containsKey("direction")) {
                FieldModel old = count;
                count = withDirection(old, direction);
                FieldModel inherited = count;
                fields.put(name, inherited);
                members.replaceAll(member -> member == old ? inherited : member);
            } else if (direction.isInput() && !count.direction().isInput()
                || direction.isOutput() && !count.direction().isOutput()) {
                problems.error(collection, prefix + "has direction " + count.direction()
                    + ", which does not include " + direction);
            }
            String previous = countUsers.putIfAbsent(name, collection.name());
            if (previous != null) {
                problems.error(collection,
                    prefix + "is already used by collection '" + previous + "'");
            }
            return problems.count() == before ? count : null;
        }

        // ---- helpers ----

        /**
         * Tells if a class has no annotated members, and no other problem explains it. A member
         * with an unresolved value or with an error is not in the list.
         */
        private boolean noMembers(List<AnnotatedMember> annotated, int errorsBefore) {
            return annotated.isEmpty() && !problems.unresolved
                && problems.count() == errorsBefore;
        }

        private static FieldModel withDirection(FieldModel field, Direction direction) {
            return new FieldModel(field.ownerSimpleName(), field.name(), field.order(),
                field.length(), direction, field.usage(), field.scale(), field.signed(),
                field.pattern(), field.kind(), field.type(), field.getter(), field.setter(),
                field.converter(), field.byteLength());
        }

        private boolean implementsInterface(TypeElement element, String interfaceName) {
            return types.isAssignable(element.asType(),
                types.erasure(elements.getTypeElement(interfaceName).asType()));
        }
    }
}
