# Architecture Specification — Vault (Phase 01)

## 1. Architectural Pattern: Clean Architecture + MVVM

Vault follows **Clean Architecture** combined with **MVVM (Model-View-ViewModel)** and **Unidirectional Data Flow (UDF)**.

```
[ UI (Composables) ] 
       ^          |
       | UiState  | User Events
       |          v
[ ViewModel (NotesViewModel) ]
       |
       v
[ Use Cases (GetNotesUseCase) ]
       |
       v
[ Repository Contract (NotesRepository) ]
       |
       v
[ Data Source / Persistence (NotesRepositoryImpl) ]
```

## 2. Key Components in Phase 01

### 2.1 Presentation (Jetpack Compose)
- Single-activity pattern (`MainActivity`).
- State is hoisted and represented as immutable data classes (`NotesUiState`).
- ViewModels emit `StateFlow<UiState>` collected with lifecycle awareness (`collectAsStateWithLifecycle()`).

### 2.2 Domain (Pure Kotlin)
- Contains business entities (`Note`) and use cases (`GetNotesUseCase`).
- Holds zero Android framework imports, maximizing unit-testability without emulators.

### 2.3 Data
- Repositories abstract persistence details behind interfaces.
- Prepared for Room SQLite integration in Phase 02.

### 2.4 Dependency Injection
- `AppContainer` interface instantiated in `VaultApplication`.
- Provides dependencies to `ViewModelProvider.Factory` without static singletons or global state.

