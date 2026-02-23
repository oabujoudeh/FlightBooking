# Git Cheat Sheet

## Morning Routine

```bash
git fetch                  # Download latest changes from remote
git branch -vv             # See which branches are behind
git pull                   # Update your current branch
```

---

## Basic Commands

| Command | What it does |
|---------|--------------|
| `git status` | See current branch, changed files, staged files |
| `git fetch` | Download updates from remote (doesn't merge) |
| `git pull` | Fetch AND merge updates into current branch |
| `git fetch --all` | Fetch from all remotes (if you have multiple) |
| `git fetch --prune` | Fetch and remove stale remote tracking branches |

---

## Branch Management

### See branches
```bash
git branch              # List local branches
git branch -a           # List local + remote branches
git branch -vv          # List branches with tracking info (shows if behind/ahead)
```

### Create a new branch
```bash
git checkout -b feature-name    # Create and switch to new branch (LOCAL ONLY)
git push -u origin feature-name # Push to remote and set up tracking
```

**Important:** `git checkout -b` only creates a LOCAL branch. The remote branch
doesn't exist until you push it with `-u` (--set-upstream). After that first push,
you can just use `git push` and `git pull` normally.

### Switch branches
```bash
git checkout main               # Switch to main
git checkout feature-name       # Switch to feature-name
```

### Delete a branch
```bash
git branch -d branch-name       # Delete local branch (safe - only if merged)
git branch -D branch-name       # Force delete local branch
git fetch --prune               # Clean up deleted remote branches
```

---

## Making Changes

### Stage changes
```bash
git add filename.txt                    # Stage specific file
git add src/main/kotlin/                # Stage a directory
git add .                               # Stage all changes (use carefully)
```

### Commit
```bash
git commit -m "Your commit message"     # Commit with message
```

### Push
```bash
git push                                # Push to tracked remote branch
git push -u origin branch-name          # Push new branch and set tracking
```

---

## Pull Requests (using GitHub CLI)

### Create a PR
```bash
gh pr create --title "PR title" --body "Description of changes"
```

### View PR status
```bash
gh pr status                            # See your PRs
gh pr view 123                          # View specific PR by number
```

---

## GitHub Issues (using GitHub CLI)

### View issues
```bash
gh issue list                           # List open issues
gh issue view 42                        # View specific issue
```

### Create a branch linked to an issue
```bash
gh issue develop 42 --checkout          # Create branch linked to issue #42 and check it out
```

This will:
- Create a new branch named after the issue (e.g., `42-issue-title`)
- Check out the branch
- Link the branch to the issue on GitHub

### Alternative: Link manually when creating PR
```bash
git checkout -b feature/issue-42-description   # Create branch normally
# ... make your changes ...
gh pr create --issue 42                        # Link to issue when creating PR
```

---

## Keeping Your Branch Up to Date

When teammates merge their code to main and you want it in your branch:

```bash
git fetch                    # Download latest from remote
git checkout main            # Switch to main
git pull                     # Update main with their changes
git checkout your-branch     # Switch back to your branch
git merge main               # Bring main's changes into your branch
```

Or the shorter way (without switching branches):

```bash
git fetch                    # Download latest
git merge origin/main        # Merge remote main directly into your branch
```

**When to do this:**
- Start of day (morning routine)
- Before creating a PR (so your branch isn't outdated)
- When you need a teammate's new feature for your work

**Visual:**
```
main:         A --- B --- C --- D (teammate's merged code)
                    \           \
your branch:         E --- F --- M (M = merge commit with their code)
```

After the merge, you have both your code AND their code.

---

## Typical Feature Workflow

### 1. Start new feature
```bash
git checkout main                       # Start from main
git pull                                # Make sure main is up to date
git checkout -b my-new-feature          # Create feature branch
```

### 2. Work on feature
```bash
# Make your changes...
git status                              # See what changed
git add file1.kt file2.kt               # Stage changes
git commit -m "Add new feature"         # Commit
```

### 3. Push and create PR
```bash
git push -u origin my-new-feature       # Push branch to remote
gh pr create --title "Add new feature" --body "Description"
```

### 4. After PR is merged
```bash
git checkout main                       # Switch to main
git pull                                # Get the merged changes
git branch -d my-new-feature            # Delete local feature branch
git fetch --prune                       # Clean up remote tracking
```

---

## Checking Status

| Command | Shows |
|---------|-------|
| `git status` | Changed/staged files |
| `git log --oneline -5` | Last 5 commits |
| `git branch -vv` | Branches with behind/ahead status |
| `git diff` | Unstaged changes |
| `git diff --staged` | Staged changes |
| `git diff main` | Changes compared to main |

---

## Undo Mistakes

```bash
git restore filename.txt                # Discard changes to a file
git restore --staged filename.txt       # Unstage a file (keep changes)
git checkout .                          # Discard ALL local changes (careful!)
```

---

## Quick Reference

```
fetch   = download updates (safe, doesn't change your code)
pull    = fetch + merge (updates your current branch)
push    = upload your commits to remote
commit  = save changes locally
branch  = create/list/delete branches
checkout = switch branches
```
