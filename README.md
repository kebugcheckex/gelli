## Project Status

This is a personal fork of [dkanada/gelli](https://github.com/dkanada/gelli), which has since been archived. I maintain it mainly for my own use and develop it with AI-assisted coding so I can iterate quickly. In practice, this means:

* Changes are committed straight to the `master` branch. There are no release branches or formal review process.
* Backward compatibility is not guaranteed. Updates may change settings, cached data, or behavior without a migration path.
* Releases are cut informally and may not follow a strict versioning scheme or come with detailed changelogs.

### Contributing

Issues and pull requests are welcome, but I may not respond to them promptly, or at all. If you want a feature or a fix, the fastest route is often to fork the repo and make the change yourself. AI coding tools work well for this, and `CLAUDE.md` gives an overview of the codebase to help them get started.

## Gelli

This is a native music player for Android devices that connects to Jellyfin media servers. The code is based on a relatively recent version of Phonograph and was originally made for personal use.

## Features

* Basic library navigation
* Cache songs individually or through batch actions
* Gapless playback
* Sort albums and songs by different fields
* Search media for partial matches
* Media service integration with notification
* Favorites and playlists
* Playback history reporting
* Filter content by library

## Issues

Since this was a small project intended mainly for myself, there are some things I haven't resolved yet. I would appreciate pull requests to fix any of these issues!

* Artist sorting isn't available through the API
* Playlists and favorites will not update automatically when changed

## Future Plans

If I ever find the time, these are some of the items I would potentially include.

* Interface overhaul
* Offline downloads
* SyncPlay
* Smart playlists
* Session controls
* QuickConnect

These are features I wouldn't include myself, but I would accept pull requests with good code.

* Support for other media types
* Chromecast
* Android Auto

## Screenshots

<img src='https://raw.githubusercontent.com/dkanada/gelli/master/metadata/en-US/screenshots.png'>
