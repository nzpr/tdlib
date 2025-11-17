# ===========================================================
#   Zig → Linux x86_64 toolchain
# ===========================================================

set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR x86_64)

find_program(ZIG NAMES zig REQUIRED)
set(ZIG_TARGET "x86_64-linux-gnu")

# Use zig cc / zig c++
set(CMAKE_C_COMPILER "${ZIG}")
set(CMAKE_C_COMPILER_ARG1 "cc")
set(CMAKE_CXX_COMPILER "${ZIG}")
set(CMAKE_CXX_COMPILER_ARG1 "c++")

# Correct target
set(CMAKE_C_COMPILER_TARGET "${ZIG_TARGET}")
set(CMAKE_CXX_COMPILER_TARGET "${ZIG_TARGET}")

# Target flags
set(CMAKE_C_FLAGS "--target=${ZIG_TARGET}")
set(CMAKE_CXX_FLAGS "--target=${ZIG_TARGET}")
set(CMAKE_EXE_LINKER_FLAGS "--target=${ZIG_TARGET}")
set(CMAKE_SHARED_LINKER_FLAGS "--target=${ZIG_TARGET}")

# Prevent macOS injection
set(CMAKE_OSX_SYSROOT "" CACHE STRING "" FORCE)
set(CMAKE_OSX_ARCHITECTURES "" CACHE STRING "" FORCE)
set(CMAKE_OSX_DEPLOYMENT_TARGET "" CACHE STRING "" FORCE)

# Force atomics detection for TDLib
set(ATOMICS_FOUND TRUE CACHE BOOL "" FORCE)

# Zig uses builtin compiler_rt for atomics
set(CMAKE_C_STANDARD_LIBRARIES "${CMAKE_C_STANDARD_LIBRARIES} -lc -lcompiler_rt")
set(CMAKE_CXX_STANDARD_LIBRARIES "${CMAKE_CXX_STANDARD_LIBRARIES} -lc -lcompiler_rt")
