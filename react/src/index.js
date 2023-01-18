import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './components/App';

const domRoot = document.getElementById('root');
domRoot.style.width = "100vw";
domRoot.style.height = "100vh";
const reactRoot = ReactDOM.createRoot(domRoot);
reactRoot.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
