# GitHub Repository Setup Guide

This guide will help you create a GitHub repository for the Hubitat Light Monitor Wrapper app.

## Steps to Create the Repository

### 1. Create a New Repository on GitHub

1. Go to [GitHub.com](https://github.com) and sign in
2. Click the **+** icon in the top right corner
3. Select **"New repository"**
4. Fill in the repository details:
   - **Repository name**: `hubitat-light-monitor-wrapper`
   - **Description**: `Hubitat Elevation app that monitors light switches to ensure they successfully turn on/off as commanded and retries if necessary`
   - **Visibility**: Choose Public or Private (Public recommended for community sharing)
   - **Initialize with**: Check "Add a README file" (we'll replace it)
   - **License**: Choose MIT License
5. Click **"Create repository"**

### 2. Upload the Files

#### Option A: Using GitHub Web Interface

1. In your new repository, click **"Add file"** → **"Upload files"**
2. Drag and drop or select the following files:
   - `Hubitat-Light-Wrapper.groovy`
   - `README.md`
   - `LICENSE`
   - `.gitignore`
3. Add a commit message: `"Initial commit: Hubitat Light Monitor Wrapper app"`
4. Click **"Commit changes"**

#### Option B: Using Git Command Line

1. Clone the repository:
   ```bash
   git clone https://github.com/YOUR_USERNAME/hubitat-light-monitor-wrapper.git
   cd hubitat-light-monitor-wrapper
   ```

2. Copy the files into the directory:
   - `Hubitat-Light-Wrapper.groovy`
   - `README.md`
   - `LICENSE`
   - `.gitignore`

3. Add and commit the files:
   ```bash
   git add .
   git commit -m "Initial commit: Hubitat Light Monitor Wrapper app"
   git push origin main
   ```

### 3. Repository Settings (Optional)

1. Go to **Settings** → **Pages**
2. Enable GitHub Pages if you want to create a documentation site
3. Go to **Settings** → **General**
4. Add topics: `hubitat`, `home-automation`, `groovy`, `smart-home`
5. Add a description and website URL if desired

### 4. Create Releases

For each version update:

1. Go to **Releases** in your repository
2. Click **"Create a new release"**
3. Tag version: `v1.05`
4. Release title: `Version 1.05 - Optimized Default Settings`
5. Description: Copy the change history from the README
6. Upload the `Hubitat-Light-Wrapper.groovy` file as a release asset
7. Click **"Publish release"**

## Repository Structure

Your repository should look like this:

```
hubitat-light-monitor-wrapper/
├── Hubitat-Light-Wrapper.groovy    # Main app file
├── README.md                        # Documentation
├── LICENSE                          # MIT License
├── .gitignore                       # Git ignore rules
└── GITHUB_SETUP.md                  # This file
```

## Sharing with the Community

Once your repository is set up, you can:

1. **Share on Hubitat Community**: Post a link to your repository
2. **Add to Hubitat Package Manager**: If applicable
3. **Create Issues**: For bug reports and feature requests
4. **Accept Pull Requests**: For community contributions

## Maintenance

- Update the version number in the app file when making changes
- Update the README.md with new features and changes
- Create releases for each version
- Respond to issues and pull requests

## Repository URL

Your repository will be available at:
`https://github.com/YOUR_USERNAME/hubitat-light-monitor-wrapper`

Replace `YOUR_USERNAME` with your actual GitHub username. 